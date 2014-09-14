package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._
import com.ambiata.mundane.control._
import scalaz._, Scalaz._, effect.IO

object SnapshotPlanner {
  def plan(repository: Repository, date: Date): ResultT[IO, Datasets] =
    ???

  /**
   * Attempt to construct the set of datasets required to be read for a snapshot, given
   * the specified feature store and incremental snapshot.
   */
  def attemptWithSnapshot(at: Date, store: FeatureStore, snapshot: Snapshot): Option[Datasets] =
    (snapshot.date.isBeforeOrEqual(at) && snapshot.store.subsetOf(store)).option(
      Datasets(Dataset.prioritizedSnapshot(snapshot) :: Dataset.within(store, snapshot.date, at))
    )

  /**
   * When we don't have any snapshot to optimise with, we fallback to reading all
   * data for the current feature store at or before the specified date.
   */
  def fallback(at: Date, features: FeatureStore): Datasets =
    Datasets(Dataset.to(features, at))
}
