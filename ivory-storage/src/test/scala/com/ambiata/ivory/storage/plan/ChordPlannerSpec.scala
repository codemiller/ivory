package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._, Arbitraries._

import org.specs2._

/*
 *


import org.scalacheck._, Arbitrary._
import com.ambiata.ivory.core.Arbitraries._
import com.ambiata.ivory.storage.Arbitraries._
*/
object ChordPlannerSpec extends Specification with ScalaCheck { def is = s2"""

  When planning a chord:
    There should be no partitions with dates after the most recent entity date    $noFuture

"""

  def noFuture = prop((entities: Entities) =>
//    ChordPlanner.plan[Id](entities)
    true)
}
