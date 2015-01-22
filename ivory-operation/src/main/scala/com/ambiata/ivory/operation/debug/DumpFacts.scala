package com.ambiata.ivory.operation.debug

import com.ambiata.mundane.control._
import com.ambiata.ivory.core._
import com.ambiata.ivory.storage.metadata._
import com.ambiata.ivory.storage.fact.Factsets

import scalaz._, Scalaz._

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
  def dump(repository: Repository, request: DumpFactsRequest, location: IvoryLocation): RIO[String \/ Unit] = for {
    output     <- location.asHdfsIvoryLocation
    hdfs       <- repository.asHdfsRepository
    dictionary <- Metadata.latestDictionaryFromIvory(repository)
    datasets   <- datasets(repository, request)
    ret        <- datasets.traverse(ds => DumpFactsJob.run(hdfs, dictionary, ds, request.entities, request.attributes, output.toHdfsPath, hdfs.root.codec))
  } yield ret

  def datasets(repository: Repository, request: DumpFactsRequest): RIO[String \/ Datasets] = for {
    factsets  <- request.factsets.traverse(fid => Factsets.factset(repository, fid))
    snapshots <- request.snapshots.traverse(sid => SnapshotStorage.byIdOrFail(repository, sid))
    pdatasets  = Prioritized.fromList(factsets.map(Dataset.factset) ++ snapshots.map(Dataset.snapshot))
    datasets   = pdatasets.cata(ds => Datasets(ds).right, "Too many factsets/snapshots!".left)
  } yield datasets
}
