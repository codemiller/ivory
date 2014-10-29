package com.ambiata.ivory.operation.extraction

import com.ambiata.ivory.core._
import com.ambiata.ivory.core.thrift._
import com.ambiata.ivory.lookup.{FeatureIdLookup, SnapshotWindowLookup, FlagLookup}
import com.ambiata.ivory.operation.extraction.snapshot._, SnapshotWritable._
import com.ambiata.ivory.storage.fact._
import com.ambiata.ivory.storage.lookup._
import com.ambiata.ivory.mr._
import com.ambiata.poacher.mr._

import java.lang.{Iterable => JIterable}
import java.util.{Iterator => JIterator}

import scala.collection.JavaConverters._
import scalaz.{Name => _, Reducer => _, _}, Scalaz._

import org.apache.hadoop.fs.Path
import org.apache.hadoop.conf._
import org.apache.hadoop.io._
import org.apache.hadoop.io.compress._
import org.apache.hadoop.mapreduce.{Counter => _, _}
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat

/**
 * This is a hand-coded MR job to squeeze the most out of snapshot performance.
 */
object SnapshotJob {
  def run(repository: HdfsRepository, conf: Configuration, dictionary: Dictionary, reducers: Int, date: Date, inputs: List[Prioritized[FactsetGlob]], output: Path,
          windows: SnapshotWindows, incremental: Option[Path], codec: Option[CompressionCodec]): Unit = {

    val job = Job.getInstance(conf)
    val ctx = MrContext.newContext("ivory-snapshot", job)

    job.setJarByClass(classOf[SnapshotReducer])
    job.setJobName(ctx.id.value)

    // map
    job.setMapOutputKeyClass(classOf[BytesWritable])
    job.setMapOutputValueClass(classOf[BytesWritable])

    // partition & sort
    job.setPartitionerClass(classOf[SnapshotWritable.PartitionerEntityFeatureId])
    job.setGroupingComparatorClass(classOf[SnapshotWritable.GroupingEntityFeatureId])
    job.setSortComparatorClass(classOf[SnapshotWritable.Comparator])

    // reducer
    job.setNumReduceTasks(reducers)
    job.setReducerClass(classOf[SnapshotReducer])
    job.setOutputKeyClass(classOf[NullWritable])
    job.setOutputValueClass(classOf[BytesWritable])

    // input
    val mappers = inputs.map(p => (classOf[SnapshotFactsetMapper], p.value))
    mappers.foreach({ case (clazz, factsetGlob) =>
      factsetGlob.keys.foreach(key => {
        println(s"Input path: ${key}")
        MultipleInputs.addInputPath(job, repository.toIvoryLocation(key).toHdfsPath, classOf[SequenceFileInputFormat[_, _]], clazz)
      })
    })

    incremental.foreach(p => {
      println(s"Incremental path: ${p}")
      MultipleInputs.addInputPath(job, p, classOf[SequenceFileInputFormat[_, _]], classOf[SnapshotIncrementalMapper])
    })

    // output
    val tmpout = new Path(ctx.output, "snap")
    job.setOutputFormatClass(classOf[SequenceFileOutputFormat[_, _]])
    FileOutputFormat.setOutputPath(job, tmpout)

    // compression
    codec.foreach(cc => {
      Compress.intermediate(job, cc)
      Compress.output(job, cc)
    })

    // cache / config initializtion
    job.getConfiguration.set(Keys.SnapshotDate, date.int.toString)
    ctx.thriftCache.push(job, Keys.FactsetLookup, FactsetLookups.priorityTable(inputs))
    ctx.thriftCache.push(job, Keys.FactsetVersionLookup, FactsetLookups.versionTable(inputs.map(_.value)))
    val (featureIdLookup, windowLookup) = windowTable(windows)
    ctx.thriftCache.push(job, Keys.FeatureIdLookup, featureIdLookup)
    ctx.thriftCache.push(job, Keys.WindowLookup, windowLookup)
    ctx.thriftCache.push(job, Keys.FeatureIsSetLookup, FeatureLookups.isSetTable(dictionary))

    // run job
    if (!job.waitForCompletion(true))
      Crash.error(Crash.ResultTIO, "ivory snapshot failed.")

    // commit files to factset
    Committer.commit(ctx, {
      case "snap" => output
    }, true).run(conf).run.unsafePerformIO
    ()
  }

  def windowTable(windows: SnapshotWindows): (FeatureIdLookup, SnapshotWindowLookup) = {
    val featureIdLookup = new FeatureIdLookup()
    // Need to ensure the map is initialized here, otherwise a dictionary with no windows will NPE out later
    val windowLookup = new SnapshotWindowLookup(new java.util.HashMap[Integer, Integer])
    windows.windows.zipWithIndex.map {
      case (SnapshotWindow(fid, w), i) =>
        featureIdLookup.putToIds(fid.toString, i)
        // If all the features have no window we still need to ensure we still have values populated
        windowLookup.putToWindow(i, w.getOrElse(Date.maxValue).int)
    }
    (featureIdLookup, windowLookup)
  }

  object Keys {
    val SnapshotDate = "ivory.snapdate"
    val FeatureIdLookup = ThriftCache.Key("feature-id-lookup")
    val FactsetLookup = ThriftCache.Key("factset-lookup")
    val FactsetVersionLookup = ThriftCache.Key("factset-version-lookup")
    val WindowLookup = ThriftCache.Key("factset-window-lookup")
    val FeatureIsSetLookup = ThriftCache.Key("feature-is-set-lookup")
  }
}

object SnapshotMapper {
  type MapperContext = Mapper[NullWritable, BytesWritable, BytesWritable, BytesWritable]#Context
}

/**
 * Factset mapper for ivory-snapshot.
 *
 * The input is a standard SequenceFileInputFormat. The path is used to determine the
 * factset/namespace/year/month/day, and a factset priority is pulled out of a lookup
 * table in the distributes cache.
 *
 * The output key is a string of entity|namespace|attribute
 *
 * The output value is expected (can not be typed checked because its all bytes) to be
 * a thrift serialized NamespacedFact object.
 */
class SnapshotFactsetMapper extends Mapper[NullWritable, BytesWritable, BytesWritable, BytesWritable] {
  import SnapshotMapper._

  /** Thrift deserializer. */
  val serializer = ThriftSerialiser()

  /** Context object holding dist cache paths */
  var ctx: MrContext = null

  /** Snapshot date, see #setup. */
  var strDate: String = null
  var date: Date = Date.unsafeFromInt(0)

  var priority = Priority.Max

  val kout = Writables.bytesWritable(4096)

  /** The output value, only create once per mapper. */
  val vout = Writables.bytesWritable(4096)

  /** Class to emit the key/value bytes, created once per mapper */
  val emitter: MrEmitter[NullWritable, BytesWritable, BytesWritable, BytesWritable] = MrEmitter()

  /** Class to count number of non skipped facts, created once per mapper */
  var okCounter: MrCounter[NullWritable, BytesWritable, BytesWritable, BytesWritable] = null

  /** Class to count number of skipped facts, created once per mapper */
  var skipCounter: MrCounter[NullWritable, BytesWritable, BytesWritable, BytesWritable] = null

  /** Thrift object provided from sub class, created once per mapper */
  val tfact = new ThriftFact

  /** Class to convert a Thrift fact into a Fact based of the version, created once per mapper */
  var converter: VersionedFactConverter = null

  val featureIdLookup = new FeatureIdLookup

  override def setup(context: MapperContext): Unit = {
    ctx = MrContext.fromConfiguration(context.getConfiguration)
    strDate = context.getConfiguration.get(SnapshotJob.Keys.SnapshotDate)
    date = Date.fromInt(strDate.toInt).getOrElse(Crash.error(Crash.DataIntegrity, s"Invalid snapshot date '${strDate}'"))
    val factsetInfo: FactsetInfo = FactsetInfo.fromMr(ctx.thriftCache, SnapshotJob.Keys.FactsetLookup,
      SnapshotJob.Keys.FactsetVersionLookup, context.getConfiguration, context.getInputSplit)
    converter = factsetInfo.factConverter
    priority = factsetInfo.priority
    okCounter = MrCounter("ivory", s"snapshot.v${factsetInfo.version}.ok")
    skipCounter = MrCounter("ivory", s"snapshot.v${factsetInfo.version}.skip")
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.FeatureIdLookup, featureIdLookup)
  }

  /**
   * Map over thrift factsets, dropping any facts in the future of `date`
   *
   * This will create two counters:
   * 1. snapshot.<version>.ok - number of facts read
   * 2. snapshot.<version>.skip - number of facts skipped because they were in the future
   */
  override def map(key: NullWritable, value: BytesWritable, context: MapperContext): Unit = {
    emitter.context = context
    okCounter.context = context
    skipCounter.context = context
    SnapshotFactsetMapper.map(tfact, date, converter, value, priority, kout, vout, emitter, okCounter, skipCounter,
      serializer, featureIdLookup)
  }
}

object SnapshotFactsetMapper {

  def map[A <: ThriftLike](tfact: ThriftFact, date: Date, converter: VersionedFactConverter, input: BytesWritable,
                           priority: Priority, kout: BytesWritable, vout: BytesWritable, emitter: Emitter[BytesWritable, BytesWritable],
                           okCounter: Counter, skipCounter: Counter, deserializer: ThriftSerialiser,
                           featureIdLookup: FeatureIdLookup) {
    deserializer.fromBytesViewUnsafe(tfact, input.getBytes, 0, input.getLength)
    val f = converter.convert(tfact)
    if(f.date > date)
      skipCounter.count(1)
    else {
      okCounter.count(1)
      KeyState.set(f, priority, kout, featureIdLookup.getIds.get(f.featureId.toString))
      val bytes = deserializer.toBytes(f.toNamespacedThrift)
      vout.set(bytes, 0, bytes.length)
      emitter.emit(kout, vout)
    }
  }
}

/**
 * Incremental snapshot mapper.
 */
class SnapshotIncrementalMapper extends Mapper[NullWritable, BytesWritable, BytesWritable, BytesWritable] {
  import SnapshotMapper._

  /** Thrift deserializer */
  val serializer = ThriftSerialiser()

  /** Output key, created once per mapper and mutated for each record */
  val kout = Writables.bytesWritable(4096)

  /** Output value, created once per mapper and mutated for each record */
  val vout = Writables.bytesWritable(4096)

  /** Empty Fact, created once per mapper and mutated for each record */
  val fact = new NamespacedThriftFact with NamespacedThriftFactDerived

  /** Class to emit the key/value bytes, created once per mapper */
  val emitter: MrEmitter[NullWritable, BytesWritable, BytesWritable, BytesWritable] = MrEmitter()

  /** Class to count number of non skipped facts, created once per mapper */
  val okCounter: MrCounter[NullWritable, BytesWritable, BytesWritable, BytesWritable] =
    MrCounter("ivory", "snapshot.incr.ok")

  val featureIdLookup = new FeatureIdLookup

  override def setup(context: MapperContext): Unit = {
    super.setup(context)
    val ctx = MrContext.fromConfiguration(context.getConfiguration)
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.FeatureIdLookup, featureIdLookup)
  }

  override def map(key: NullWritable, value: BytesWritable, context: MapperContext): Unit = {
    emitter.context = context
    okCounter.context = context
    SnapshotIncrementalMapper.map(fact, value, Priority.Max, kout, vout, emitter, okCounter, serializer, featureIdLookup)
  }
}

object SnapshotIncrementalMapper {

  def map(fact: NamespacedThriftFact with NamespacedThriftFactDerived, bytes: BytesWritable, priority: Priority,
          kout: BytesWritable, vout: BytesWritable, emitter: Emitter[BytesWritable, BytesWritable], okCounter: Counter,
          serializer: ThriftSerialiser, featureIdLookup: FeatureIdLookup) {
    okCounter.count(1)
    fact.clear()
    serializer.fromBytesViewUnsafe(fact, bytes.getBytes, 0, bytes.getLength)
    KeyState.set(fact, priority, kout, featureIdLookup.getIds.get(fact.featureId.toString))
    // Pass through the bytes
    vout.set(bytes.getBytes, 0, bytes.getLength)
    emitter.emit(kout, vout)
  }
}

/**
 * Reducer for ivory-snapshot.
 *
 * This reducer takes the latest fact with the same entity|namespace|attribute key
 *
 * The input values are serialized containers of factset priority and bytes of serialized NamespacedFact.
 *
 * The output is a sequence file, with no key, and the bytes of the serialized NamespacedFact.
 */
class SnapshotReducer extends Reducer[BytesWritable, BytesWritable, NullWritable, BytesWritable] {
  import SnapshotReducer._

  /** Thrift deserializer */
  val serializer = ThriftSerialiser()

  /** Empty Fact, created once per reducer and mutated per record */
  val fact = new NamespacedThriftFact with NamespacedThriftFactDerived

  /** Output value, created once per reducer and mutated per record */
  val vout = Writables.bytesWritable(4096)

  /** Class to emit the key/value bytes, created once per mapper */
  val emitter: MrEmitter[BytesWritable, BytesWritable, NullWritable, BytesWritable] = MrEmitter()

  val mutator = new FactByteMutator

  /** Optimised array lookup for features to the window, where we know that features are simply ordered from zero */
  var windowLookup: Array[Int] = null

  /** Optimised array lookup to flag "Set" features vs "State" features. */
  var isSetLookup: Array[Boolean] = null

  override def setup(context: ReducerContext): Unit = {
    val ctx = MrContext.fromConfiguration(context.getConfiguration)

    val windowLookupThrift = new SnapshotWindowLookup
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.WindowLookup, windowLookupThrift)
    windowLookup = windowLookupToArray(windowLookupThrift)

    val isSetLookupThrift = new FlagLookup
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.FeatureIsSetLookup, isSetLookupThrift)
    isSetLookup = FeatureLookups.isSetLookupToArray(isSetLookupThrift)
  }

  override def reduce(key: BytesWritable, iter: JIterable[BytesWritable], context: ReducerContext): Unit = {
    emitter.context = context
    val feature = SnapshotWritable.GroupingEntityFeatureId.getFeatureId(key)
    val windowStart = Date.unsafeFromInt(windowLookup(feature))
    SnapshotReducer.reduce(fact, iter.iterator, mutator, emitter, vout, windowStart, isSetLookup(feature))
  }
}

/** ***************** !!!!!! WARNING !!!!!! ******************
 *
 * There is some nasty mutation in here that can corrupt data
 * without knowing, so double/triple check with others when
 * changing.
 *
 ********************************************************** */
object SnapshotReducer {
  type ReducerContext = Reducer[BytesWritable, BytesWritable, NullWritable, BytesWritable]#Context

  val sentinelDateTime = DateTime.unsafeFromLong(-1)

  def reduce[A](fact: MutableFact, iter: JIterator[A], mutator: PipeFactMutator[A, A],
                emitter: Emitter[NullWritable, A], out: A, windowStart: Date, isSet: Boolean): Unit = {
    var datetime = sentinelDateTime
    val kout = NullWritable.get()
    while(iter.hasNext) {
      val next = iter.next
      mutator.from(next, fact)
      // Respect the "highest" priority (ie. the first fact with any given datetime), unless this is a set
      // then we want to include every value (at least until we have keyed sets, see https://github.com/ambiata/ivory/issues/376).
      if (datetime != fact.datetime || isSet) {
        // If the _current_ fact is in the window we still want to emit the _previous_ fact which may be
        // the last fact within the window, or another fact within the window
        // As such we can't do anything on the first fact
        if (datetime != sentinelDateTime && windowStart.underlying <= fact.datetime.date.underlying) {
          emitter.emit(kout, out)
        }
        datetime = fact.datetime
        // Store the current fact, which may or may not be emitted depending on the next fact
        mutator.pipe(next, out)
      }
    }
    // _Always_ emit the last fact, which will be within the window, or the last fact
    emitter.emit(kout, out)
  }

  def windowLookupToArray(lookup: SnapshotWindowLookup): Array[Int] =
    sparseMapToArray(lookup.getWindow.asScala.map { case (i, v) => i.toInt -> v.toInt}.toList, Date.maxValue.int)

  def sparseMapToArray[A : scala.reflect.ClassTag](map: List[(Int, A)], default: A): Array[A] = {
    val max = map.map(_._1).max
    val array = Array.fill(max + 1)(default)
    map.foreach {
      case (i, a) => array(i) = a
    }
    array
  }
}