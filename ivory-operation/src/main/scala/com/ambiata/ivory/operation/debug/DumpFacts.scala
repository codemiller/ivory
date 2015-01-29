package com.ambiata.ivory.operation.debug

import com.ambiata.mundane.control._
import com.ambiata.mundane.io.DirPath
import com.ambiata.ivory.core._
import com.ambiata.ivory.storage.metadata._
import com.ambiata.ivory.storage.fact._
import com.ambiata.ivory.operation.extraction.IvoryInputs
import com.ambiata.ivory.operation.display.Print
import com.ambiata.poacher.hdfs.Hdfs
import com.ambiata.poacher.mr.Writables

import org.apache.hadoop.io.{BytesWritable, NullWritable, IntWritable, Writable}
import org.apache.hadoop.fs.Path
import org.apache.hadoop.conf.Configuration

import scalaz._, Scalaz._, effect.IO

/**
 * Details of a debug-dump-facts request.
 *  - 'factsets' is the list of factsets to include.
 *  - 'snapshots' is the list of snapshots to include.
 *  - 'entities' is the list of entities to filter by, an empty list implies all entities.
 *  - 'attributes' is the list of attributes to filter by, an empty list implies all attributes.
 */
case class DumpFactsRequest(
  factsets: List[FactsetId]
, snapshots: List[SnapshotId]
, entities: List[String]
, attributes: List[String]
)

object DumpFacts {
  def dumpToFile(repository: Repository, request: DumpFactsRequest, location: IvoryLocation): RIO[String \/ Unit] = for {
    output     <- location.asHdfsIvoryLocation
    hdfs       <- repository.asHdfsRepository
    dictionary <- Metadata.latestDictionaryFromIvory(repository)
    datasets   <- datasets(repository, request)
    ret        <- datasets.traverse(ds => DumpFactsJob.run(hdfs, dictionary, ds, request.entities, request.attributes, output.toHdfsPath, hdfs.root.codec))
  } yield ret

  def dumpToStdout(repository: Repository, request: DumpFactsRequest): RIO[String \/ Unit] = for {
    hdfs       <- repository.asHdfsRepository
    dictionary <- Metadata.latestDictionaryFromIvory(repository)
    datasets   <- datasets(repository, request)
    ret        <- datasets match {
      case -\/(e)  =>
        RIO.ok(e.left)
      case \/-(ds) => for {
        fs <- ds.factsets.traverse(factset => printFactset(repository, factset, request)).map(_.sequenceU.void)
        ss <- ds.snapshots.traverse(snapshot => printSnapshot(repository, snapshot, request)).map(_.sequenceU.void)
      } yield fs.flatMap(_ => ss)
    }
  } yield ret

  def datasets(repository: Repository, request: DumpFactsRequest): RIO[String \/ Datasets] = for {
    factsets  <- request.factsets.traverse(fid => Factsets.factset(repository, fid))
    snapshots <- request.snapshots.traverse(sid => SnapshotStorage.byIdOrFail(repository, sid))
    pdatasets  = Prioritized.fromList(factsets.map(Dataset.factset) ++ snapshots.map(Dataset.snapshot))
    datasets   = pdatasets.cata(ds => Datasets(ds).right, "Too many factsets/snapshots!".left)
  } yield datasets

  def print[K <: Writable, V <: Writable](fact: MutableFact, converter: MrFactConverter[K, V], mapper: DumpFactsMapper)(key: K, value: V): IO[Unit] = {
    converter.convert(fact, key, value)
    IO.putStrLn(if(mapper.accept(fact)) mapper.render(fact) else "")
  }

  def printFactset(repository: Repository, factset: Factset, request: DumpFactsRequest): RIO[String \/ Unit] = {
    val mapper = DumpFactsMapper(request.entities.toSet, request.attributes.toSet, s"Factset[${factset.id.render}]")
    repository match {
      case hr @ HdfsRepository(_) => for {
        paths <- IvoryInputs.factsetPaths(hr, factset).traverse(expandPath).map(_.flatten).run(hr.configuration)
        ret   <- paths.traverse(path => factset.format match {
          case FactsetFormat.V1 => printV1Factset(path, hr.configuration, mapper)
          case FactsetFormat.V2 => printV2Factset(path, hr.configuration, mapper)
        }).map(_.sequenceU.void)
      } yield ret
      case LocalRepository(_) => RIO.ok("DumpFacts does not support reading factsets from local repositories yet".left)
      case S3Repository(_) => RIO.ok("DumpFacts does not support reading factsets from s3 repositories yet".left)
    }
  }

  def printV1Factset(path: Path, config: Configuration, mapper: DumpFactsMapper): RIO[String \/ Unit] = {
    Partition.parseDir(DirPath.unsafe(path.toString)).disjunction.traverse(partition => {
      val converter = PartitionFactConverter(partition)
      Print.printWith(path, config, NullWritable.get, Writables.bytesWritable(4096))(print(createMutableFact, converter, mapper) _)
    })
  }

  def printV2Factset(path: Path, config: Configuration, mapper: DumpFactsMapper): RIO[String \/ Unit] = {
    Partition.parseDir(DirPath.unsafe(path.toString)).disjunction.traverse(partition => {
      val converter = PartitionFactConverter(partition)
      Print.printWith(path, config, NullWritable.get, Writables.bytesWritable(4096))(print(createMutableFact, converter, mapper) _)
    })
  }

  def printSnapshot(repository: Repository, snapshot: Snapshot, request: DumpFactsRequest): RIO[String \/ Unit] = {
    val mapper = DumpFactsMapper(request.entities.toSet, request.attributes.toSet, s"Snapshot[${snapshot.id.render}]")
    repository match {
      case hr @ HdfsRepository(_) => for {
        paths <- IvoryInputs.snapshotPaths(hr, snapshot).traverse(expandPath).map(_.flatten).run(hr.configuration)
        ret   <- paths.traverse(path => snapshot.format match {
          case SnapshotFormat.V1 => printV1Snapshot(path, hr.configuration, mapper)
          case SnapshotFormat.V2 => printV2Snapshot(path, hr.configuration, mapper)
        }).map(_.sequenceU.void)
      } yield ret
      case LocalRepository(_) => RIO.ok("DumpFacts does not support reading snapshots from local repositories yet".left)
      case S3Repository(_) => RIO.ok("DumpFacts does not support reading snapshots from s3 repositories yet".left)
    }
  }

  def printV1Snapshot(path: Path, config: Configuration, mapper: DumpFactsMapper): RIO[String \/ Unit] = {
    val converter = MutableFactConverter()
    Print.printWith(path, config, NullWritable.get, Writables.bytesWritable(4096))(print(createMutableFact, converter, mapper) _).map(_.right)
  }

  def printV2Snapshot(path: Path, config: Configuration, mapper: DumpFactsMapper): RIO[String \/ Unit] = {
    Namespace.nameFromStringDisjunction(path.getName).traverse(namespace => {
      val converter = NamespaceDateFactConverter(namespace)
      Print.printWith(path, config, new IntWritable, Writables.bytesWritable(4096))(print(createMutableFact, converter, mapper) _)
    })
  }

  def expandPath(path: Path): Hdfs[List[Path]] = {
    val (base, glob) = Hdfs.pathAndGlob(path)
    Hdfs.globPaths(base, glob)
  }
}
