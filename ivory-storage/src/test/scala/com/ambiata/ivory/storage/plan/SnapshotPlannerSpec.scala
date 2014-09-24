package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._, Arbitraries._

import org.specs2._
import org.scalacheck._, Arbitrary._

object SnapshotPlannerSpec extends Specification with ScalaCheck { def is = s2"""

  Planner must satisfy the following rules:
    ${"""1. If the latest FeatureStore equals the closest Snapshot
         FeatureStore AND the given Date equals the Snapshot Date,
         return only the SnapshotDataset.                                       """!rule1}
    ${"""2. If the latest FeatureStore (s1) equals the closest Snapshot
         FeatureStore (s2) AND the given Date (t1) does not equal the
         Snapshot Date (t2), return a FactsetDataset for each of the
         factsets in FeatureStore s1 containing partitions from t2 up to
         t1 along with the SnapshotDataset.                                     """!rule2}
    ${"""3. If the latest FeatureStore (s1) does not equal the closest
         Snapshot FeatureStore (s2), return a FactsetDataset for each
         factset which does not appear in both s1 and s2, containing
         partitions up to the given Date (t1). If the Snapshot Date (t2)
         does not equal the given Date (t1), also return a FactsetDataset
         for each factset which intersets s1 and s2, containing partitions
         from t2 up to t1. Also return the SnapshotDataset.                     """!rule3}
    ${"""4. If there are no Snapshot Dates which are less then or equal to
         the given Date (t1), or the given incremental flag is set to false,
         return a FactsetDataset for each factset in the latest FeatureStore
         containing all partitions up to t1.                                    """!rule4}
    ${"""5. If the latest FeatureStore (s1) does not contain ALL of the
         factsets in the latest Snapshot FeatureStore (s2), either find a
         previous Snapshot (sn) and apply rule 3 to s1 and sn, or apply
         rule 4.                                                                """!rule5}

    Attempting to build a dataset form a snapshot:
      Never build a dataset if the snapshot store is not a subset of the current store    $subset
      Never include a snpashot from the future                                            $future
      When sucessful, output datasets must not incliude any date after 'at' date          $snapshot

    No valid snapshot Fallback behaviour:
      Output datasets must not include any date after 'at' date                           $fallback

"""
  def rule1 =
    pending

  def rule2 =
    pending

  def rule3 =
    pending

  def rule4 =
    pending

  def rule5 =
    pending

  def subset = prop((at: Date, store: FeatureStore, snapshot: Snapshot) => !snapshot.store.subsetOf(store) ==>
    !SnapshotPlanner.attemptWithSnapshot(at, store, snapshot).isDefined)

  def future = prop((at: Date, store: FeatureStore, snapshot: Snapshot) => snapshot.date.isAfter(at) ==>
    !SnapshotPlanner.attemptWithSnapshot(at, store, snapshot).isDefined)

  def snapshot = prop((at: Date, store: FeatureStore, snapshot: Snapshot) => (snapshot.date.isBeforeOrEqual(at) && snapshot.store.subsetOf(store)) ==>
    SnapshotPlanner.attemptWithSnapshot(at, store, snapshot).exists(allBefore(at)))

  def fallback = prop((at: Date, store: FeatureStore) =>
    allBefore(at) { SnapshotPlanner.fallback(at, store) })

  def allBefore(at: Date): Datasets => Boolean =
    datasets => datasets.sets.forall({
      case Prioritized(_, FactsetDataset(factset)) =>
        factset.partitions.forall(_.date.isBeforeOrEqual(at))
      case Prioritized(_, SnapshotDataset(snapshot)) =>
        snapshot.date.isBeforeOrEqual(at)
    })
}
