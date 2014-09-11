package com.ambiata.ivory.core

sealed trait Dataset
case class FactsetDataset(factset: Factset) extends Dataset
case class SnapshotDataset(snapshot: Snapshot) extends Dataset

object Dataset {
  def factset(factset: Factset): Dataset =
    FactsetDataset(factset)

  def snapshot(snapshot: Snapshot): Dataset =
    SnapshotDataset(snapshot)

  def prioritizedSnapshot(snapshot: Snapshot): Prioritized[Dataset] =
    Prioritized(Priority.Max, SnapshotDataset(snapshot))

  def within(features: FeatureStore, after: Date, to: Date): List[Prioritized[Dataset]] =
    features.filterByDate(date => date.isAfter(after) && date.isBeforeOrEqual(to)).toDataset

  def to(features: FeatureStore, to: Date): List[Prioritized[Dataset]] =
    features.filterByDate(_.isBeforeOrEqual(to)).toDataset
}
