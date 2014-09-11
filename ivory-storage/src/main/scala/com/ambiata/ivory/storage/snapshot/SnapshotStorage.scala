package com.ambiata.ivory.storage.snapshot

import com.ambiata.ivory.core._
import com.ambiata.ivory.storage.metadata._
import com.ambiata.mundane.control._

import scalaz._, effect.IO

object SnpapshotStorage {
  def getById(repository: Repository, id: SnapshotId): ResultT[IO, Snapshot] =
    SnapshotMetadataStorage.getById(repository, id).flatMap(getByMetadata(repository, _))

  def getByMetadata(repository: Repository, metadata: SnapshotMetadata): ResultT[IO, Snapshot] =
    FeatureStoreTextStorage.fromId(repository, metadata.storeId).map(f =>
      Snapshot(metadata.id, metadata.date, f))
}
