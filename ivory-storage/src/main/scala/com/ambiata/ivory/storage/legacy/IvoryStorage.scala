package com.ambiata.ivory.storage.legacy

import com.ambiata.ivory.core._
import com.ambiata.ivory.core.thrift._
import com.ambiata.ivory.storage.control._
import com.ambiata.ivory.storage.fact._
import com.ambiata.mundane.control._
import com.ambiata.poacher.scoobi._
import com.nicta.scoobi.Scoobi._
import org.apache.hadoop.io.compress._
import scalaz.{DList => _, _}


trait IvoryScoobiLoader[A] {
  def loadScoobi(implicit sc: ScoobiConfiguration): DList[ParseError \/ A]
}

trait IvoryScoobiStorer[A, +B] {
  def storeScoobi(dlist: DList[A])(implicit sc: ScoobiConfiguration): B
  def storeMeta: ScoobiAction[Unit] =
    ScoobiAction.ok(())
}

object IvoryStorage {
  // this is the version that factsets are written as
  val factsetVersion =
    FactsetVersionTwo

  def factsetStorer(path: String, codec: Option[CompressionCodec]): IvoryScoobiStorer[Fact, DList[(PartitionKey, ThriftFact)]] =
    PartitionFactThriftStorageV2.PartitionedFactThriftStorer(path, codec)

  /**
   * Get the loader for a given version
   */

  def writeFactsetVersion(repo: Repository, factsets: List[FactsetId]): ResultTIO[Unit] =
    Versions.writeAll(repo, factsets, factsetVersion)

  def writeFactsetVersionI(factsets: List[FactsetId]): IvoryTIO[Unit] =
    IvoryT.fromResultT(writeFactsetVersion(_, factsets))

  implicit class IvoryFactStorage(dlist: DList[Fact]) {
    def toIvoryFactset(repo: HdfsRepository, factset: FactsetId, codec: Option[CompressionCodec])(implicit sc: ScoobiConfiguration): DList[(PartitionKey, ThriftFact)] =
      IvoryStorage.factsetStorer(repo.factset(factset).path, codec).storeScoobi(dlist)
  }
}
