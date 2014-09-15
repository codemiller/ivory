package com.ambiata.ivory.storage.plan

import org.specs2._
/*
 * import com.ambiata.ivory.core._


import org.scalacheck._, Arbitrary._
import com.ambiata.ivory.core.Arbitraries._
import com.ambiata.ivory.storage.Arbitraries._
*/
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

"""

  def rule1 =
    false

  def rule2 =
    false

  def rule3 =
    false

  def rule4 =
    false

  def rule5 =
    false
}
