package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._
import scalaz._, Scalaz._

object SnapshotPlanner {
  /**
   * Attempt to construct the set of datasets required to be read for a snapshot, given
   * the specified feature store and incremental snapshot.
   */
  def attemptWithSnapshot(at: Date, features: FeatureStore, snapshot: Snapshot): Option[Datasets] =
    (snapshot.date.isBeforeOrEqual(at) && snapshot.features.subsetOf(features)).option(
      Datasets(Dataset.prioritizedSnapshot(snapshot) :: Dataset.within(features, snapshot.date, at))
    )

  /**
   * When we don't have any snapshot to optimise with, we fallback to reading all
   * data for the current feature store at or before the specified date.
   */
  def fallback(at: Date, features: FeatureStore): Datasets =
    Datasets(Dataset.to(features, at))
}
