package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._, Lists.findMapM
import com.ambiata.mundane.control._
import scalaz._, Scalaz._, effect.IO

object SnapshotPlanner {
  def plan(repository: Repository, date: Date): ResultT[IO, Datasets] =
    ???

  def planX[F[+_]: Monad](at: Date, store: FeatureStore, snapshots: List[SnapshotMetadata], getSnapshot: Kleisli[F, SnapshotId, Snapshot]): F[Datasets] = {
    val candidates = snapshots.filter(_.date <= at).sortBy(metadata => ~metadata.date.int)
    findMapM(candidates)(metadata =>
      getSnapshot(metadata.id).map(snapshot => attemptWithSnapshot(at, store, snapshot))
    ).map(_.getOrElse(fallback(at, store)))
  }

  /**
   * Attempt to construct the set of datasets required to be read for a snapshot, given
   * the specified feature store and incremental snapshot.
   */
  def attemptWithSnapshot(at: Date, store: FeatureStore, snapshot: Snapshot): Option[Datasets] =
    (snapshot.date.isBeforeOrEqual(at) && snapshot.store.subsetOf(store)).option(
      Datasets(Dataset.prioritizedSnapshot(snapshot) :: Dataset.within(store, snapshot.date, at)))

  /**
   * When we don't have any snapshot to optimise with, we fallback to reading all
   * data for the current feature store at or before the specified date.
   */
  def fallback(at: Date, store: FeatureStore): Datasets =
    Datasets(Dataset.to(store, at))
}
