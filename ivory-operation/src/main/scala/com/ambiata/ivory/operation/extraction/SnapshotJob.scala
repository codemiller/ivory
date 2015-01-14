package com.ambiata.ivory.operation.extraction

import com.ambiata.ivory.core._
import com.ambiata.ivory.core.thrift._
import com.ambiata.ivory.lookup.{FeatureIdLookup, SnapshotWindowLookup, FlagLookup}
import com.ambiata.ivory.operation.extraction.snapshot._, SnapshotWritable._
import com.ambiata.ivory.storage.fact._
import com.ambiata.ivory.storage.lookup._
import com.ambiata.ivory.storage.plan._
import com.ambiata.ivory.mr._
import com.ambiata.mundane.control._
import com.ambiata.poacher.mr._

import java.lang.{Iterable => JIterable}
import java.util.{Iterator => JIterator}

import scala.collection.JavaConverters._

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io._
import org.apache.hadoop.mapreduce.{Counter => _, _}
import org.apache.hadoop.mapreduce.lib.output.{FileOutputFormat, SequenceFileOutputFormat, MultipleOutputs, LazyOutputFormat}

/**
 * This is a hand-coded MR job to squeeze the most out of snapshot performance.
 */
object SnapshotJob {

  def run(repository: HdfsRepository, plan: SnapshotPlan, reducers: Int, output: Path): RIO[SnapshotStats] = {

    val job = Job.getInstance(repository.configuration)
    val ctx = MrContextIvory.newContext("ivory-snapshot", job)

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
    val incrementalMapper = plan.snapshot.map(_.format match {
      case SnapshotFormat.V1 => classOf[SnapshotV1IncrementalMapper]
      case SnapshotFormat.V2 => classOf[SnapshotV2IncrementalMapper]
    }).getOrElse(classOf[SnapshotV1IncrementalMapper])
    IvoryInputs.configure(ctx, job, repository, plan.datasets, classOf[SnapshotFactsetMapper], incrementalMapper)

    // output
    LazyOutputFormat.setOutputFormatClass(job, classOf[SequenceFileOutputFormat[_, _]])
    MultipleOutputs.addNamedOutput(job, Keys.Out, classOf[SequenceFileOutputFormat[_, _]],  classOf[IntWritable], classOf[BytesWritable])
    FileOutputFormat.setOutputPath(job, ctx.output)

    // compression
    repository.codec.foreach(cc => {
      Compress.intermediate(job, cc)
      Compress.output(job, cc)
    })

    // cache / config initializtion
    job.getConfiguration.set(Keys.SnapshotDate, plan.date.int.toString) // FIX why int toString and not render/parse date?
    ctx.thriftCache.push(job, Keys.FactsetLookup, FactsetLookups.priorityTable(plan.datasets))
    ctx.thriftCache.push(job, Keys.FactsetVersionLookup, FactsetLookups.versionTable(plan.datasets))
    val (featureIdLookup, windowLookup) = windowTable(plan.commit.dictionary.value, plan.commit.dictionary.value.windows.byFeature(plan.date))
    ctx.thriftCache.push(job, Keys.FeatureIdLookup, featureIdLookup)
    ctx.thriftCache.push(job, Keys.WindowLookup, windowLookup)
    ctx.thriftCache.push(job, Keys.FeatureIsSetLookup, FeatureLookups.isSetTable(plan.commit.dictionary.value))

    // run job
    if (!job.waitForCompletion(true))
      Crash.error(Crash.RIO, "ivory snapshot failed.")

    // commit files to factset
    for {
      _ <- Committer.commit(ctx, {
        case Keys.Out => output
      }, true).run(repository.configuration)
      now <- RIO.fromIO(DateTime.now)
    } yield {
      val featureIdToName = featureIdLookup.ids.asScala.mapValues(_.toString).map(_.swap)
      SnapshotStats(IvoryVersion.get, now, job.getCounters.getGroup(Keys.CounterFeatureGroup).iterator().asScala.flatMap {
        c => featureIdToName.get(c.getName).flatMap(FeatureId.parse(_).toOption).map(_ -> c.getValue)
      }.toMap)
    }
  }

  def windowTable(dictionary: Dictionary, ranges: Ranges[FeatureId]): (FeatureIdLookup, SnapshotWindowLookup) = {
    val featureIdLookup = FeatureLookups.featureIdTable(dictionary)
    val windowLookup = new SnapshotWindowLookup(new java.util.HashMap[Integer, Integer])
    ranges.values.foreach(range => {
      val id = featureIdLookup.getIds.get(range.id.toString)
      windowLookup.putToWindow(id, range.fromOrMax.int)
    })
    (featureIdLookup, windowLookup)
  }

  object Keys {
    val SnapshotDate = "ivory.snapdate"
    val CounterFeatureGroup = "snapshot-counter-features"
    val FeatureIdLookup = ThriftCache.Key("feature-id-lookup")
    val FactsetLookup = ThriftCache.Key("factset-lookup")
    val FactsetVersionLookup = ThriftCache.Key("factset-version-lookup")
    val WindowLookup = ThriftCache.Key("factset-window-lookup")
    val FeatureIsSetLookup = ThriftCache.Key("feature-is-set-lookup")
    val Out = "snap"
  }
}

object SnapshotMapper {
  type MapperContext[K <: Writable] = Mapper[K, BytesWritable, BytesWritable, BytesWritable]#Context
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
class SnapshotFactsetMapper extends CombinableMapper[NullWritable, BytesWritable, BytesWritable, BytesWritable] {
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
  var okCounter: Counter = null

  /** Class to count number of skipped facts, created once per mapper */
  var skipCounter: Counter = null

  /** Class to count number of dropped facts that don't appear in dictionary anymore, created once per mapper */
  var dropCounter: Counter = null

  /** Thrift object provided from sub class, created once per mapper */
  val tfact = new ThriftFact

  /** Class to convert a Thrift fact into a Fact based of the version, created once per mapper */
  var converter: VersionedFactConverter = null

  val featureIdLookup = new FeatureIdLookup

  override def setupSplit(context: MapperContext[NullWritable], split: InputSplit): Unit = {
    ctx = MrContext.fromConfiguration(context.getConfiguration)
    strDate = context.getConfiguration.get(SnapshotJob.Keys.SnapshotDate)
    date = Date.fromInt(strDate.toInt).getOrElse(Crash.error(Crash.DataIntegrity, s"Invalid snapshot date '${strDate}'"))
    val factsetInfo: FactsetInfo = FactsetInfo.fromMr(ctx.thriftCache, SnapshotJob.Keys.FactsetLookup,
      SnapshotJob.Keys.FactsetVersionLookup, context.getConfiguration, split)
    converter = factsetInfo.factConverter
    priority = factsetInfo.priority
    okCounter = MrCounter("ivory", s"snapshot.v${factsetInfo.version}.ok", context)
    skipCounter = MrCounter("ivory", s"snapshot.v${factsetInfo.version}.skip", context)
    dropCounter = MrCounter("ivory", "drop", context)
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.FeatureIdLookup, featureIdLookup)
  }

  /**
   * Map over thrift factsets, dropping any facts in the future of `date`
   *
   * This will create two counters:
   * 1. snapshot.<version>.ok - number of facts read
   * 2. snapshot.<version>.skip - number of facts skipped because they were in the future
   */
  override def map(key: NullWritable, value: BytesWritable, context: MapperContext[NullWritable]): Unit = {
    emitter.context = context
    SnapshotFactsetMapper.map(tfact, date, converter, value, priority, kout, vout, emitter, okCounter, skipCounter, dropCounter,
      serializer, featureIdLookup)
  }
}

object SnapshotFactsetMapper {

  def map[A <: ThriftLike](tfact: ThriftFact, date: Date, converter: VersionedFactConverter, input: BytesWritable,
                           priority: Priority, kout: BytesWritable, vout: BytesWritable, emitter: Emitter[BytesWritable, BytesWritable],
                           okCounter: Counter, skipCounter: Counter, dropCounter: Counter, deserializer: ThriftSerialiser,
                           featureIdLookup: FeatureIdLookup) {
    deserializer.fromBytesViewUnsafe(tfact, input.getBytes, 0, input.getLength)
    val f = converter.convert(tfact)
    val name = f.featureId.toString
    val featureId = featureIdLookup.getIds.get(name)
    if (featureId == null)
      dropCounter.count(1)
    else if (f.date > date)
      skipCounter.count(1)
    else {
      okCounter.count(1)
      KeyState.set(f, priority, kout, featureId)
      val bytes = deserializer.toBytes(f.toNamespacedThrift)
      vout.set(bytes, 0, bytes.length)
      emitter.emit(kout, vout)
    }
  }
}

/**
 * Incremental snapshot mapper.
 */
abstract class SnapshotIncrementalMapper[K <: Writable] extends CombinableMapper[K, BytesWritable, BytesWritable, BytesWritable] {
  import SnapshotMapper._

  /** Thrift deserializer */
  val serializer = ThriftSerialiser()

  /** Output key, created once per mapper and mutated for each record */
  val kout = Writables.bytesWritable(4096)

  /** Output value, created once per mapper and mutated for each record */
  val vout = Writables.bytesWritable(4096)

  /** Class to emit the key/value bytes, created once per mapper */
  val emitter: MrEmitter[K, BytesWritable, BytesWritable, BytesWritable] = MrEmitter()

  /** Class to count number of non skipped facts, created once per mapper */
  var okCounter: Counter = null

  /** Class to count number of dropped facts that don't appear in dictionary anymore, created once per mapper */
  var dropCounter: Counter = null

  val featureIdLookup = new FeatureIdLookup

  override def setupSplit(context: MapperContext[K], split: InputSplit): Unit = {
    super.setup(context)
    val ctx = MrContext.fromConfiguration(context.getConfiguration)
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.FeatureIdLookup, featureIdLookup)
    okCounter = MrCounter("ivory", "snapshot.incr.ok", context)
    dropCounter = MrCounter("ivory", "drop", context)
  }
}

class SnapshotV1IncrementalMapper extends SnapshotIncrementalMapper[NullWritable] {
  import SnapshotMapper._

  /** Empty Fact, created once per mapper and mutated for each record */
  val fact = new NamespacedThriftFact with NamespacedThriftFactDerived

  override def map(key: NullWritable, value: BytesWritable, context: MapperContext[NullWritable]): Unit = {
    emitter.context = context
    SnapshotIncrementalMapper.mapV1(fact, value, Priority.Max, kout, vout, emitter, okCounter, dropCounter, serializer, featureIdLookup)
  }
}

class SnapshotV2IncrementalMapper extends SnapshotIncrementalMapper[IntWritable] {
  import SnapshotMapper._

  /** Thrift object provided from sub class, created once per mapper */
  val tfact = new ThriftFact

  /** Namespace of each Fact, created once per mapper, taken from split path */
  var namespace: String = null

  override def setupSplit(context: MapperContext[IntWritable], split: InputSplit): Unit = {
    super.setupSplit(context, split)
    namespace = Namespace.nameFromStringDisjunction(MrContext.getSplitPath(split).getParent.getName) match {
      case scalaz.\/-(n) => n.name
      case scalaz.-\/(e) => Crash.error(Crash.DataIntegrity, s"Can not parse snapshot namespace from path ${e}")
    }
  }

  override def map(key: IntWritable, value: BytesWritable, context: MapperContext[IntWritable]): Unit = {
    emitter.context = context
    SnapshotIncrementalMapper.mapV2(tfact, key, value, Priority.Max, kout, vout, emitter, okCounter, dropCounter, serializer, featureIdLookup, namespace)
  }
}

object SnapshotIncrementalMapper {
  def mapV1(fact: NamespacedThriftFact with NamespacedThriftFactDerived, bytes: BytesWritable, priority: Priority,
          kout: BytesWritable, vout: BytesWritable, emitter: Emitter[BytesWritable, BytesWritable], okCounter: Counter,
          dropCounter: Counter, serializer: ThriftSerialiser, featureIdLookup: FeatureIdLookup) {
    serializer.fromBytesViewUnsafe(fact, bytes.getBytes, 0, bytes.getLength)
    emit(fact, bytes, priority, kout, vout, emitter, okCounter, dropCounter, serializer, featureIdLookup)
  }

  def mapV2(tfact: ThriftFact, key: IntWritable, value: BytesWritable, priority: Priority, kout: BytesWritable,
            vout: BytesWritable, emitter: Emitter[BytesWritable, BytesWritable], okCounter: Counter, dropCounter: Counter,
            serializer: ThriftSerialiser, featureIdLookup: FeatureIdLookup, namespace: String) {
    serializer.fromBytesViewUnsafe(tfact, value.getBytes, 0, value.getLength)
    val date = Date.unsafeFromInt(key.get)
    val fact = FatThriftFact(namespace, date, tfact)
    val bytes = serializer.toBytes(fact)

    // WARNING reusing input value BytesWritable
    value.set(bytes, 0, bytes.length)
    emit(fact, value, priority, kout, vout, emitter, okCounter, dropCounter, serializer, featureIdLookup)
  }

  def emit(fact: Fact, bytes: BytesWritable, priority: Priority, kout: BytesWritable, vout: BytesWritable, emitter: Emitter[BytesWritable, BytesWritable],
           okCounter: Counter, dropCounter: Counter, serializer: ThriftSerialiser, featureIdLookup: FeatureIdLookup) {
    val name = fact.featureId.toString
    val featureId = featureIdLookup.getIds.get(name)
    if (featureId == null)
      dropCounter.count(1)
    else {
      okCounter.count(1)
      KeyState.set(fact, priority, kout, featureId)
      // Pass through the bytes
      vout.set(bytes.getBytes, 0, bytes.getLength)
      emitter.emit(kout, vout)
    }
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
class SnapshotReducer extends Reducer[BytesWritable, BytesWritable, IntWritable, BytesWritable] {
  import SnapshotReducer._

  /** Thrift deserializer */
  val serializer = ThriftSerialiser()

  /** Empty Fact, created once per reducer and mutated per record */
  val fact = new NamespacedThriftFact with NamespacedThriftFactDerived

  /** Output key, created once per reducer and mutated per record */
  val kout = new IntWritable(0)

  /** Output value, created once per reducer and mutated per record */
  val vout = Writables.bytesWritable(4096)

  /** Class to emit the key/value bytes, created once per mapper */
  var emitter: MrMultiEmitter[IntWritable, BytesWritable] = null

  val mutator = new ThriftByteMutator

  /** Optimised array lookup for features to the window, where we know that features are simply ordered from zero */
  var windowLookup: Array[Int] = null

  /** Optimised array lookup to flag "Set" features vs "State" features. */
  var isSetLookup: Array[Boolean] = null
  var feaureIdStrings: Array[String] = null

  var featureCounter: LabelledCounter = null

  override def setup(context: ReducerContext): Unit = {
    val ctx = MrContext.fromConfiguration(context.getConfiguration)

    val windowLookupThrift = new SnapshotWindowLookup
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.WindowLookup, windowLookupThrift)
    windowLookup = windowLookupToArray(windowLookupThrift)

    val isSetLookupThrift = new FlagLookup
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.FeatureIsSetLookup, isSetLookupThrift)
    isSetLookup = FeatureLookups.isSetLookupToArray(isSetLookupThrift)

    featureCounter = new MrLabelledCounter(SnapshotJob.Keys.CounterFeatureGroup, context)
    // Kinda sucky for debugging - we're using ints for counters because Hadoop limits the size of counter names to 64
    feaureIdStrings = FeatureLookups.sparseMapToArray(windowLookupThrift.getWindow.asScala.toList
      .map { case (fid, _) => fid.intValue() -> fid.toString }, "")
  
    emitter = MrMultiEmitter(new MultipleOutputs(context))
  }

  override def reduce(key: BytesWritable, iter: JIterable[BytesWritable], context: ReducerContext): Unit = {
    val feature = SnapshotWritable.GroupingEntityFeatureId.getFeatureId(key)
    val windowStart = Date.unsafeFromInt(windowLookup(feature))
    val count = SnapshotReducer.reduce(fact, iter.iterator, mutator, emitter, kout, vout, windowStart, isSetLookup(feature))
// FIX MAX COUNTERS    featureCounter.count(feaureIdStrings(feature), count)
    ()
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
  type ReducerContext = Reducer[BytesWritable, BytesWritable, IntWritable, BytesWritable]#Context

  val sentinelDateTime = DateTime.unsafeFromLong(-1)

  def reduce(fact: MutableFact, iter: JIterator[BytesWritable], mutator: ThriftByteMutator,
             emitter: MultiEmitter[IntWritable, BytesWritable], kout: IntWritable, vout: BytesWritable, windowStart: Date, isSet: Boolean): Int = {
    emitter.name = SnapshotJob.Keys.Out
    var datetime = sentinelDateTime
    var count = 0
    while(iter.hasNext) {
      val next = iter.next
      mutator.from(next, fact)
      // Respect the "highest" priority (ie. the first fact with any given datetime), unless this is a set
      // then we want to include every value (at least until we have keyed sets, see https://github.com/ambiata/ivory/issues/376).
      if (datetime != fact.datetime || isSet) {
        // If the _current_ fact is in the window we still want to emit the _previous_ fact which may be
        // the last fact within the window, or another fact within the window
        // As such we can't do anything on the first fact
        if (datetime != sentinelDateTime && Window.isFactWithinWindow(windowStart, fact)) {
          emitter.emit(kout, vout)
          count += 1
        }
        datetime = fact.datetime
        // Store the current fact, which may or may not be emitted depending on the next fact
        kout.set(fact.date.int)
        mutator.mutate(fact.toThrift, vout)
        // emit to namespaced subdirs
        emitter.path = fact.namespace.name
      }
    }
    // _Always_ emit the last fact, which will be within the window, or the last fact
    emitter.emit(kout, vout)
    count += 1
    count
  }

  def windowLookupToArray(lookup: SnapshotWindowLookup): Array[Int] =
    FeatureLookups.sparseMapToArray(lookup.getWindow.asScala.map { case (i, v) => i.toInt -> v.toInt}.toList, Date.maxValue.int)
}
