package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._

sealed trait Dataset
case class FactsetDataset(factset: Factset) extends Dataset
case class SnapshotDataset(snapshot: Snapshot) extends Dataset

object Dataset {
  def factset(factset: Factset): Dataset =
    FactsetDataset(factset)

  def snapshot(snapshot: Snapshot): Dataset =
    SnapshotDataset(snapshot)
}
