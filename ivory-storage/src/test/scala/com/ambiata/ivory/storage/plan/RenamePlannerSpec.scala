package com.ambiata.ivory.storage.plan

import org.specs2._
/*
 * import com.ambiata.ivory.core._


import org.scalacheck._, Arbitrary._
import com.ambiata.ivory.core.Arbitraries._
import com.ambiata.ivory.storage.Arbitraries._
*/
object RenamePlannerSpec extends Specification with ScalaCheck { def is = s2"""

  When planning a rename:
    There should be no snapshots read as we are just updating factsets          $noSnapshots
    There should be no namespaces in plan that are not in required list         $requiredOnly
    If a required namespace appears in store, it should appear in plan          $allRequired

"""

  def noSnapshots =
    false

  def requiredOnly =
    false

  def allRequired =
    false
}
