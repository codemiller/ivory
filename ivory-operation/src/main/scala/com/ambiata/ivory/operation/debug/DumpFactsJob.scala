package com.ambiata.ivory.operation.debug

import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import com.ambiata.ivory.core._
import com.ambiata.ivory.core.thrift._
import com.ambiata.ivory.mr.MrContextIvory
import com.ambiata.ivory.storage.fact._
import com.ambiata.ivory.storage.metadata._
import com.ambiata.ivory.storage.repository.HdfsGlobs.FactsetPartitionsGlob
import com.ambiata.poacher.mr._

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.compress._
import org.apache.hadoop.mapreduce._
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat

import scalaz._, Scalaz._

object DumpFactsJob {
  def run(
    repository: HdfsRepository
  , dictionary: Dictionary
  , request: DumpFactsRequest
  , output: Path
  , codec: Option[CompressionCodec]
  ): RIO[Unit] = for {
    job <- RIO.io { Job.getInstance(repository.configuration) }
    ctx <- RIO.io { MrContextIvory.newContext("ivory-dump-facts", job) }
    ss  <- request.snapshots.traverse(id => SnapshotStorage.byIdOrFail(repository, id))
    r   <- RIO.io {
        job.setJarByClass(classOf[DumpFactsSnapshotMapper[_]])
        job.setJobName(ctx.id.value)
        job.setMapOutputKeyClass(classOf[NullWritable])
        job.setMapOutputValueClass(classOf[Text])
        job.setNumReduceTasks(0)
        request.factsets.foreach(id => {
          val base = repository.toIvoryLocation(Repository.factset(id)).toHdfsPath
          val path = new Path(base, FactsetPartitionsGlob)
          MultipleInputs.addInputPath(job, path, classOf[SequenceFileInputFormat[_, _]], classOf[DumpFactsFactsetMapper])
        })
        ss.foreach(snapshot => {
          val paths = snapshot.location.map(k => repository.toIvoryLocation(k).toHdfsPath)
          val mapperClass = snapshot.info.format match {
            case SnapshotFormat.V1 => classOf[DumpFactsSnapshotV1Mapper]
            case SnapshotFormat.V2 => classOf[DumpFactsSnapshotV2Mapper]
          }
          paths.foreach(p => MultipleInputs.addInputPath(job, p, classOf[SequenceFileInputFormat[_, _]], mapperClass))
        })
        val tmpout = new Path(ctx.output, "dump-facts")
        job.setOutputFormatClass(classOf[TextOutputFormat[_, _]])
        FileOutputFormat.setOutputPath(job, tmpout)
        codec.foreach(cc => {
          Compress.intermediate(job, cc)
          Compress.output(job, cc)
        })
        write(job.getConfiguration, Keys.Entities, request.entities)
        write(job.getConfiguration, Keys.Attributes, request.attributes)

        job.waitForCompletion(true)
      }
    _   <- RIO.unless(r, RIO.fail("Ivory dump facts failed to complete, please see job tracker."))
    _   <- Committer.commit(ctx, {
          case "dump-facts" => output
        }, true).run(repository.configuration)
  } yield ()

  def write(c: Configuration, k: String, vs: List[String]): Unit =
    c.set(k, vs.mkString(","))

  def read(c: Configuration, k: String): List[String] =
    // getOrElse("") required to handle hadoop null-ing of empty strings in config
    Option(c.get(k)).getOrElse("").split(",").toList.filter(!_.isEmpty)

  object Keys {
    val Attributes = "ivory.dump-facts.attributes"
    val Entities = "ivory.dump-facts.entities"
  }
}

abstract class DumpFactsSnapshotMapper[K <: Writable] extends Mapper[K, BytesWritable, NullWritable, Text] {
  val serializer = ThriftSerialiser()
  val buffer = new StringBuilder(4096)
  val key = NullWritable.get
  val out = new Text
  val missing = "NA"

  var mapper: DumpFactsMapper = null
  override def setup(context: Mapper[K, BytesWritable, NullWritable, Text]#Context): Unit = {
    val path = MrContext.getSplitPath(context.getInputSplit)
    val id = SnapshotId.parse(FilePath.unsafe(path.toString).dirname.components.last).getOrElse(Crash.error(Crash.DataIntegrity, s"Can not parse snapshot id from path: ${path}"))
    val source = s"Snapshot[${id.render}]"
    val entities = DumpFactsJob.read(context.getConfiguration, DumpFactsJob.Keys.Entities).toSet
    val attributes = DumpFactsJob.read(context.getConfiguration, DumpFactsJob.Keys.Attributes).toSet
    mapper = DumpFactsMapper(entities, attributes, source)
  }

  def write(fact: Fact, context: Mapper[K, BytesWritable, NullWritable, Text]#Context): Unit = {
    if (mapper.accept(fact)) {
      out.set(mapper.renderWith(fact, buffer))
      context.write(key, out)
    }
  }
}

class DumpFactsSnapshotV1Mapper extends DumpFactsSnapshotMapper[NullWritable] {
  val fact = new NamespacedThriftFact with NamespacedThriftFactDerived
  override def map(key: NullWritable, value: BytesWritable, context: Mapper[NullWritable, BytesWritable, NullWritable, Text]#Context): Unit = {
    serializer.fromBytesViewUnsafe(fact, value.getBytes, 0, value.getLength)
    write(fact, context)
  }
}

class DumpFactsSnapshotV2Mapper extends DumpFactsSnapshotMapper[IntWritable] {
  val tfact = new ThriftFact
  var namespace: String = null

  override def setup(context: Mapper[IntWritable, BytesWritable, NullWritable, Text]#Context): Unit = {
    super.setup(context)
    namespace = Namespace.nameFromStringDisjunction(MrContext.getSplitPath(context.getInputSplit).getParent.getName) match {
      case scalaz.\/-(n) => n.name
      case scalaz.-\/(e) => Crash.error(Crash.DataIntegrity, s"Can not parse snapshot namespace from path ${e}")
    }
  }

  override def map(key: IntWritable, value: BytesWritable, context: Mapper[IntWritable, BytesWritable, NullWritable, Text]#Context): Unit = {
    serializer.fromBytesViewUnsafe(tfact, value.getBytes, 0, value.getLength)
    val date = Date.unsafeFromInt(key.get)
    val fact = FatThriftFact(namespace, date, tfact)
    write(fact, context)
  }
}

class DumpFactsFactsetMapper extends Mapper[NullWritable, BytesWritable, NullWritable, Text] {
  val serializer = ThriftSerialiser()
  val buffer = new StringBuilder(4096)
  val underlying = new ThriftFact
  val key = NullWritable.get
  val out = new Text
  var fact: MutableFact = null
  var mapper: DumpFactsMapper = null

  override def setup(context: Mapper[NullWritable, BytesWritable, NullWritable, Text]#Context): Unit = {
    val (id, p) = FactsetInfo.getBaseInfo(context.getInputSplit)
    val source = s"Factset[${id.render}]"
    val entities = DumpFactsJob.read(context.getConfiguration, DumpFactsJob.Keys.Entities).toSet
    val attributes = DumpFactsJob.read(context.getConfiguration, DumpFactsJob.Keys.Attributes).toSet
    mapper = DumpFactsMapper(entities, attributes, source)
    fact = FatThriftFact(p.namespace.name, p.date, underlying)
  }

  override def map(key: NullWritable, value: BytesWritable, context: Mapper[NullWritable, BytesWritable, NullWritable, Text]#Context): Unit = {
    serializer.fromBytesViewUnsafe(underlying, value.getBytes, 0, value.getLength)
    if (mapper.accept(fact)) {
      out.set(mapper.renderWith(fact, buffer))
      context.write(key, out)
    }
  }
}
