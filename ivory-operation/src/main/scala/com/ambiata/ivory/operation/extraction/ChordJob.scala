package com.ambiata.ivory.operation.extraction

import com.ambiata.ivory.core._
import com.ambiata.ivory.core.thrift._
import com.ambiata.ivory.lookup._
import com.ambiata.ivory.operation.extraction.chord._
import com.ambiata.ivory.operation.extraction.snapshot.{SnapshotReader, SnapshotWritable}
import com.ambiata.ivory.storage.fact._
import com.ambiata.ivory.storage.lookup._
import com.ambiata.ivory.storage.plan._
import com.ambiata.ivory.storage.entities._
import com.ambiata.ivory.mr._
import com.ambiata.mundane.control._
import com.ambiata.poacher.mr._

import java.lang.{Iterable => JIterable}
import java.util.{Iterator => JIterator}

import scala.collection.JavaConverters._

import org.apache.hadoop.fs.Path
import org.apache.hadoop.conf._
import org.apache.hadoop.io._
import org.apache.hadoop.mapreduce.{Counter => _, _}
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat

/**
 * This is a hand-coded MR job to squeeze the most out of chord performance.
 *
 * This uses the SnapshotWritable in the exact same way snapshot does
 */
object ChordJob {
  def run(repository: HdfsRepository, plan: ChordPlan, reducers: Int, output: Path): RIO[Unit] = {
    val job = Job.getInstance(repository.configuration)
    val ctx = MrContextIvory.newContext("ivory-chord", job)

    job.setJarByClass(classOf[ChordReducer])
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
    job.setReducerClass(classOf[ChordReducer])
    job.setOutputKeyClass(classOf[NullWritable])
    job.setOutputValueClass(classOf[BytesWritable])

    // input
    IvoryInputs.configure(ctx, job, repository, plan.datasets, {
      case FactsetFormat.V1 => classOf[ChordV1FactsetMapper]
      case FactsetFormat.V2 => classOf[ChordV2FactsetMapper]
    }, {
      case SnapshotFormat.V1 => classOf[ChordV1IncrementalMapper]
      case SnapshotFormat.V2 => classOf[ChordV2IncrementalMapper]
    })

    // output
    val tmpout = new Path(ctx.output, "chord")
    job.setOutputFormatClass(classOf[SequenceFileOutputFormat[_, _]])
    FileOutputFormat.setOutputPath(job, tmpout)

    // compression
    repository.codec.foreach(cc => {
      Compress.intermediate(job, cc)
      Compress.output(job, cc)
    })

    // cache / config initializtion
    ctx.thriftCache.push(job, Keys.FactsetLookup, FactsetLookups.priorityTable(plan.datasets))
    ctx.thriftCache.push(job, Keys.FactsetVersionLookup, FactsetLookups.versionTable(plan.datasets))
    ctx.thriftCache.push(job, Keys.FeatureIdLookup, featureIdLookup(plan.commit.dictionary.value))
    ctx.thriftCache.push(job, Keys.ChordEntitiesLookup, Entities.toChordEntities(plan.entities))
    ctx.thriftCache.push(job, Keys.FeatureIsSetLookup, FeatureLookups.isSetTable(plan.commit.dictionary.value))
    ctx.thriftCache.push(job, Keys.ChordWindowsLookup, FeatureLookups.windowTable(plan.commit.dictionary.value))

    // run job
    if (!job.waitForCompletion(true))
      Crash.error(Crash.RIO, "ivory chord failed.")

    // commit files to factset
    Committer.commit(ctx, {
      case "chord" => output
    }, true).run(repository.configuration)
  }

  def featureIdLookup(dict: Dictionary): FeatureIdLookup =
    new FeatureIdLookup(dict.byFeatureIndexReverse.map({ case (k, v) => (k.toString, Int.box(v)) }).asJava)

  def setupEntities(thriftCache: ThriftCache, configuration: Configuration): Entities = {
    val chordEntities = new ChordEntities
    thriftCache.pop(configuration, ChordJob.Keys.ChordEntitiesLookup, chordEntities)
    Entities.fromChordEntities(chordEntities)
  }

  object Keys {
    val ChordDate = "ivory.chorddate"
    val FeatureIdLookup = ThriftCache.Key("feature-id-lookup")
    val FactsetLookup = ThriftCache.Key("factset-lookup")
    val FactsetVersionLookup = ThriftCache.Key("factset-version-lookup")
    val ChordEntitiesLookup = ThriftCache.Key("chord-entities-lookup")
    val FeatureIsSetLookup = ThriftCache.Key("feature-is-set-lookup")
    val ChordWindowsLookup = ThriftCache.Key("chord-window-lookup")
  }
}

object ChordMapper {
  type MapperContext[K <: Writable] = Mapper[K, BytesWritable, BytesWritable, BytesWritable]#Context

}

/**
 * Factset mapper for ivory-chord.
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
abstract class ChordFactsetMapper[K <: Writable] extends CombinableMapper[K, BytesWritable, BytesWritable, BytesWritable] {
  import ChordMapper._

  /** Thrift deserializer. */
  val serializer = ThriftSerialiser()

  /** Context object holding dist cache paths */
  var ctx: MrContext = null

  var priority = Priority.Max

  val kout = Writables.bytesWritable(4096)

  /** The output value, only create once per mapper. */
  val vout = Writables.bytesWritable(4096)

  /** Class to emit the key/value bytes, created once per mapper */
  val emitter: MrEmitter[K, BytesWritable, BytesWritable, BytesWritable] = MrEmitter()

  /** Class to count number of non skipped facts, created once per mapper */
  var okCounter: MrCounter[K, BytesWritable, BytesWritable, BytesWritable] = null

  /** Class to count number of skipped facts, created once per mapper */
  var skipCounter: MrCounter[K, BytesWritable, BytesWritable, BytesWritable] = null

  /** Class to count number of dropped facts that don't appear in dictionary anymore, created once per mapper */
  var dropCounter: Counter = null

  /** Thrift object provided from sub class, created once per mapper */
  val fact: MutableFact = createMutableFact

  /** Class to convert a key/value into a Fact based of the version, created once per mapper */
  var converter: MrFactConverter[K, BytesWritable] = null

  val featureIdLookup = new FeatureIdLookup

  var entities: Entities = null

  /** The partition this mapper is reading from, created once per input split */
  var partition: Partition = null

  /** The format the mapper is reading from, set once per mapper from the subclass */
  val format: FactsetFormat

  override def setup(context: MapperContext[K]): Unit = {
    ctx = MrContext.fromConfiguration(context.getConfiguration)
    ctx.thriftCache.pop(context.getConfiguration, ChordJob.Keys.FeatureIdLookup, featureIdLookup)
    entities = ChordJob.setupEntities(ctx.thriftCache, context.getConfiguration)
  }

  override def setupSplit(context: MapperContext[K], split: InputSplit): Unit = {
    val factsetInfo: FactsetInfo = FactsetInfo.fromMr(ctx.thriftCache, ChordJob.Keys.FactsetLookup, context.getConfiguration, split)
    okCounter = MrCounter("ivory", s"chord.v${format.toStringFormat}.ok", context)
    skipCounter = MrCounter("ivory", s"chord.v${format.toStringFormat}.skip", context)
    dropCounter = MrCounter("ivory", "drop", context)
    partition = factsetInfo.partition
    priority = factsetInfo.priority
  }

  /**
   * Map over thrift factsets, dropping any facts in the future of `date`
   *
   * This will create two counters:
   * 1. chord.<version>.ok - number of facts read
   * 2. chord.<version>.skip - number of facts skipped because they were in the future
   */
  override def map(key: K, value: BytesWritable, context: MapperContext[K]): Unit = {
    emitter.context = context
    ChordFactsetMapper.map(fact, converter, key, value, priority, kout, vout, emitter, okCounter, skipCounter, dropCounter, serializer,
      featureIdLookup, entities)
  }
}

class ChordV1FactsetMapper extends ChordFactsetMapper[NullWritable] {
  import ChordMapper._
  val format: FactsetFormat = FactsetFormat.V1
  override def setupSplit(context: MapperContext[NullWritable], split: InputSplit): Unit = {
    super.setupSplit(context, split)
    converter = PartitionFactConverter(partition)
  }
}
class ChordV2FactsetMapper extends ChordFactsetMapper[NullWritable] {
  import ChordMapper._
  val format: FactsetFormat = FactsetFormat.V2
  override def setupSplit(context: MapperContext[NullWritable], split: InputSplit): Unit = {
    super.setupSplit(context, split)
    converter = PartitionFactConverter(partition)
  }
}

object ChordFactsetMapper {

  def map[K <: Writable](fact: MutableFact, converter: MrFactConverter[K, BytesWritable], key: K, value: BytesWritable, priority: Priority,
          kout: BytesWritable, vout: BytesWritable, emitter: Emitter[BytesWritable, BytesWritable], okCounter: Counter,
          skipCounter: Counter, dropCounter: Counter, deserializer: ThriftSerialiser, featureIdLookup: FeatureIdLookup, entities: Entities) {
    converter.convert(fact, key, value, deserializer)
    val name = fact.featureId.toString
    val featureId = featureIdLookup.getIds.get(name)
    if (featureId == null)
      dropCounter.count(1)
    else if (!entities.keep(fact))
      skipCounter.count(1)
    else {
      okCounter.count(1)
      SnapshotWritable.KeyState.set(fact, priority, kout, featureId)
      val bytes = deserializer.toBytes(fact)
      vout.set(bytes, 0, bytes.length)
      emitter.emit(kout, vout)
    }
  }
}

/**
 * Incremental chord mapper.
 */
abstract class ChordIncrementalMapper[K <: Writable] extends CombinableMapper[K, BytesWritable, BytesWritable, BytesWritable] {
  import ChordMapper._

  /** Thrift deserializer */
  val serializer = ThriftSerialiser()

  /** Empty Fact, created once per mapper and mutated for each record */
  val fact = createMutableFact

  /** Output key, created once per mapper and mutated for each record */
  val kout = Writables.bytesWritable(4096)

  /** Output value, created once per mapper and mutated for each record */
  val vout = Writables.bytesWritable(4096)

  /** Class to emit the key/value bytes, created once per mapper */
  val emitter: MrEmitter[K, BytesWritable, BytesWritable, BytesWritable] = MrEmitter()

  /** Class to count number of non skipped facts, created once per mapper */
  var okCounter: Counter = null

  /** Class to count number of skipped facts, created once per mapper */
  var skipCounter: Counter =  null

  /** Class to count number of dropped facts that don't appear in dictionary anymore, created once per mapper */
  var dropCounter: Counter = null

  val featureIdLookup = new FeatureIdLookup

  var entities: Entities = null

  /** Class to convert a key/value into a Fact based of the version, created once per mapper */
  var converter: MrFactConverter[K, BytesWritable] = null

  override def setupSplit(context: MapperContext[K], split: InputSplit): Unit = {
    super.setup(context)
    val ctx = MrContext.fromConfiguration(context.getConfiguration)
    ctx.thriftCache.pop(context.getConfiguration, ChordJob.Keys.FeatureIdLookup, featureIdLookup)
    entities = ChordJob.setupEntities(ctx.thriftCache, context.getConfiguration)
    okCounter = MrCounter("ivory", "chord.incr.ok", context)
    skipCounter = MrCounter("ivory", "chord.incr.skip", context)
    dropCounter = MrCounter("ivory", "drop", context)
  }

  override def map(key: K, value: BytesWritable, context: MapperContext[K]): Unit = {
    emitter.context = context
    ChordIncrementalMapper.map(fact, key, value, Priority.Max, kout, vout, emitter, okCounter, skipCounter, dropCounter, serializer, featureIdLookup, entities, converter)
  }
}

class ChordV1IncrementalMapper extends ChordIncrementalMapper[NullWritable] {
  import ChordMapper._
  override def setupSplit(context: MapperContext[NullWritable], split: InputSplit): Unit = {
    super.setupSplit(context, split)
    converter = MutableFactConverter()
  }
}

class ChordV2IncrementalMapper extends ChordIncrementalMapper[IntWritable] {
  import ChordMapper._
  override def setupSplit(context: MapperContext[IntWritable], split: InputSplit): Unit = {
    super.setupSplit(context, split)
    converter = NamespaceDateFactConverter(Namespaces.fromSnapshotMr(split))
  }
}

object ChordIncrementalMapper {

  def map[K <: Writable](fact: MutableFact, key: K, value: BytesWritable, priority: Priority, kout: BytesWritable, vout: BytesWritable,
                         emitter: Emitter[BytesWritable, BytesWritable], okCounter: Counter, skipCounter: Counter, dropCounter: Counter,
                         serializer: ThriftSerialiser, featureIdLookup: FeatureIdLookup, entities: Entities, converter: MrFactConverter[K, BytesWritable]) {
    converter.convert(fact, key, value, serializer)
    val name = fact.featureId.toString
    val featureId = featureIdLookup.getIds.get(name)
    if (featureId == null)
      dropCounter.count(1)
    else if(!entities.keep(fact))
      skipCounter.count(1)
    else {
      okCounter.count(1)
      SnapshotWritable.KeyState.set(fact, priority, kout, featureIdLookup.getIds.get(fact.featureId.toString))
      val bytes = serializer.toBytes(fact)
      vout.set(bytes, 0, bytes.length)
      emitter.emit(kout, vout)
    }
  }
}

/**
 * Reducer for ivory-chord.
 *
 * This reducer takes the latest fact with the same entity|namespace|attribute key
 *
 * The input values are serialized containers of factset priority and bytes of serialized NamespacedFact.
 *
 * The output is a sequence file, with no key, and the bytes of the serialized NamespacedFact.
 */
class ChordReducer extends Reducer[BytesWritable, BytesWritable, NullWritable, BytesWritable] {
  import ChordReducer._

  /** Thrift deserializer */
  val serializer = ThriftSerialiser()

  /** Empty Fact, created once per reducer and mutated per record */
  val fact = new NamespacedThriftFact with NamespacedThriftFactDerived

  /** Output value, created once per reducer and mutated per record */
  val vout = Writables.bytesWritable(4096)

  /** Class to emit the key/value bytes, created once per mapper */
  val emitter: MrEmitter[BytesWritable, BytesWritable, NullWritable, BytesWritable] = MrEmitter()

  val mutator = new ThriftByteMutator

  var entities: Entities = null
  var featureWindows: Array[Option[Date => Date]] = null
  /** Shared array which can be re-used which is allocated size of the largest number of chords for a single entity */
  var windows: Array[Int] = null
  var chordEmitter: ChordWindowEmitter = null

  val buffer = new StringBuilder(4096)

  /** Optimised array lookup to flag "Set" features vs "State" features. */
  var isSetLookup: Array[Boolean] = null

  override def setup(context: ReducerContext): Unit = {
    val ctx = MrContext.fromConfiguration(context.getConfiguration)
    entities = ChordJob.setupEntities(ctx.thriftCache, context.getConfiguration)

    val isSetLookupThrift = new FlagLookup
    ctx.thriftCache.pop(context.getConfiguration, SnapshotJob.Keys.FeatureIsSetLookup, isSetLookupThrift)
    isSetLookup = FeatureLookups.isSetLookupToArray(isSetLookupThrift)

    featureWindows = ChordReducer.setupWindows(ctx.thriftCache, context.getConfiguration).map(_.map(a => (b: Date) => Window.startingDate(a, b)))
    windows = new Array(entities.maxChordSize)
    chordEmitter = new ChordWindowEmitter(emitter)
  }

  override def reduce(key: BytesWritable, iter: JIterable[BytesWritable], context: ReducerContext): Unit = {
    emitter.context = context
    val entity = SnapshotWritable.GroupingEntityFeatureId.getEntity(key)
    val feature = SnapshotWritable.GroupingEntityFeatureId.getFeatureId(key)

    val chords = entities.entities.get(entity)

    val featureId = SnapshotWritable.GroupingEntityFeatureId.getFeatureId(key)
    val dateLookup = featureWindows(featureId)
    // Using isDefined/get to avoid function allocation :(
    if (dateLookup.isDefined)
      ChordWindows.updateWindowsForChords(chords, dateLookup.get, windows)
    val windowStarts = if (dateLookup.isDefined) windows else null

    ChordReducer.reduce(fact, iter.iterator, mutator, chordEmitter, vout, chords, windowStarts, buffer, isSetLookup(feature))
  }
}

/** ***************** !!!!!! WARNING !!!!!! ******************
 *
 * There is some nasty mutation in here that can corrupt data
 * without knowing, so double/triple check with others when
 * changing.
 *
 ********************************************************** */
object ChordReducer {
  type ReducerContext = Reducer[BytesWritable, BytesWritable, NullWritable, BytesWritable]#Context

  def setupWindows(thriftCache: ThriftCache, configuration: Configuration): Array[Option[Window]] = {
    val windows = new SnapshotWindowLookup
    thriftCache.pop(configuration, ChordJob.Keys.ChordWindowsLookup, windows)
    windowLookupToArray(windows)
  }

  def windowLookupToArray(windows: SnapshotWindowLookup): Array[Option[Window]] =
    FeatureLookups.sparseMapToArray(windows.window.asScala.map {
      case (fid, w) => fid.toInt ->  WindowLookup.fromInt(w)
    }.toList, None)

  val sentinelDateTime = DateTime.unsafeFromLong(-1)

  class ChordWindowEmitter(emitter: Emitter[NullWritable, BytesWritable]) {
    val kout = NullWritable.get()

    /** `windowStarts` will be the same length as `dates`, or `null` if no window is set for the current feature. */
    def emit(fact: MutableFact, mutator: ThriftByteMutator, out: BytesWritable, dates: Array[Int], windowStarts: Array[Int],
             buffer: StringBuilder, previousDatetime: DateTime, date: Date, offset: Int): Int = {
      var i = offset
      // For window features _always_ emit the last fact before the window (for state-based features)
      // Keep in mind that this will _also_ handily emit the previous fact when it _is_ in the window
      var canEmit = windowStarts != null && Window.withinWindow(Date.unsafeFromInt(windowStarts(i)), date)
      while (i >= 0 && date.underlying > dates(i)) {
        // For both types of features we _always_ want to emit the last fact for a given chord (it may be the only one)
        canEmit = true
        i = i - 1
      }
      if (canEmit) {
        // Because we're not messing with the entity ids any more we only need to emit the fact once
        // We can also emit directly what's currently stored in 'out' - no need to hydrate back into 'fact'
        emitter.emit(kout, out)
      }
      i
    }
  }

  def reduce(fact: MutableFact, iter: JIterator[BytesWritable], mutator: ThriftByteMutator,
             emitter: ChordWindowEmitter, out: BytesWritable, dates: Array[Int], windowStarts: Array[Int],
             buffer: StringBuilder, isSet: Boolean): Unit = {

    /**
     * Entity ids need to be appended with the date in the chord file as its possible to have the same entity
     * id with multiple dates in the chord file.
     */
    def emitEntity(previousDatetime: DateTime, date: Date, offset: Int): Int = {
      // If the first chord has no matches there won't be anything to emit
      // It also covers the (otherwise impossible) case that the iterator is empty
      if (previousDatetime != sentinelDateTime) {
        emitter.emit(fact, mutator, out, dates, windowStarts, buffer, previousDatetime, date, offset)
      } else {
        var i = offset
        // Ignore any old chords that don't have a matching fact
        while (i >= 0 && date.underlying > dates(i)) {
          i = i - 1
        }
        i
      }
    }
    // dates are ordered latest to earliest, but we want it the other way around
    var i = dates.length - 1
    var previousDatetime = sentinelDateTime
    while (iter.hasNext) {
      val next = iter.next
      mutator.from(next, fact)
      val datetime = fact.datetime
      // facts are in priority order already, so this simply takes the top priority when there is a date/time clash
      if (datetime != previousDatetime || isSet) {
        i = emitEntity(previousDatetime, datetime.date, i)
        previousDatetime = datetime
        // Store this fact to be emitted if we can't find a better match
        mutator.pipe(next, out)
      }
    }
    // Flush the remaining chords
    emitEntity(previousDatetime, Date.maxValue, i)
    ()
  }
}
