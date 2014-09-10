package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._
import scalaz._, Scalaz._

object SnapshotPlanner {
  def attemptWithSnapshot(at: Date, features: FeatureStore, snapshot: Snapshot): Option[Datasets] =
    (snapshot.date.isBeforeOrEqual(at) && ???).option(
      ???
    )
//    Datasets(features.factsets.map(_.map(_.filter(_.data.isBeforeOrEqual(at)))))

  /**
   * When we don't have any snapshot to optimise with, we fallback to reading all
   * data for the current feature store at or before the specified date.
   */
  def fallback(at: Date, features: FeatureStore): Datasets =
    Datasets(features.factsets.map(prioritized =>
      prioritized.map(factset =>
        Dataset.factset(factset.filter(partition =>
          partition.date.isBeforeOrEqual(at))))))
}
