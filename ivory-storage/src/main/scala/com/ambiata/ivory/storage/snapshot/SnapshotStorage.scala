package com.ambiata.ivory.storage.snapshot

import com.ambiata.ivory.core._
import com.ambiata.mundane.control._

import scalaz._, effect.IO

object SnpapshotStorage {
  def metadata(repository: Repository): ResultT[IO, List[SnapshotMetadata]] =
    ???

  def getById(id: SnapshotId): ResultT[IO, Snapshot] =
    ???


}
