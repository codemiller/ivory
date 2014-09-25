package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._, Lists.findMapM
import scalaz._, Scalaz._

/**
 * This planner is responsible for producing the minimal set of data to read for a
 * snapshot operation.
 */
object SnapshotPlanner {

  /**
   * Determine the plan datasets for the given snapshot 'at' date, and repository
   * state.
   */
  def plan[F[+_]: Monad](at: Date, store: FeatureStore, snapshots: List[SnapshotMetadata], getSnapshot: Kleisli[F, SnapshotId, Snapshot]): F[Datasets] = {
    val candidates = snapshots.filter(_.date <= at).sortBy(metadata => ~metadata.date.int)
    findMapM(candidates)(metadata =>
      getSnapshot(metadata.id).map(snapshot => build(at, store, snapshot))
    ).map(_.getOrElse(fallback(at, store)))
  }

  /**
   * Attempt to construct the set of datasets required to be read for a snapshot, given
   * the specified feature store and incremental snapshot. This shall build a data set
   * iff if the snapshot 'isValid' for the given date and store. The dataset shall be
   * the _minimal_ dataset required for the operation.
   */
  def build(at: Date, store: FeatureStore, snapshot: Snapshot): Option[Datasets] =
    isValid(at, store, snapshot).option(
      (Datasets(Dataset.prioritizedSnapshot(snapshot) :: Dataset.within(store, snapshot.date, at))).prune)

  /**
   * Determine if a given snapshot is valid for the specified snapshot 'at' date
   * and store.
   */
  def isValid(at: Date, store: FeatureStore, snapshot: Snapshot): Boolean =
    snapshot.date.isBeforeOrEqual(at) && snapshot.store.subsetOf(store)

  /**
   * When we don't have any snapshot to optimise with, we fallback to reading all
   * data for the current feature store at or before the specified date.
   */
  def fallback(at: Date, store: FeatureStore): Datasets =
    Datasets(Dataset.to(store, at)).prune
}
