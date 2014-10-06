package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._

import org.specs2._
import org.scalacheck._, Arbitrary._

object RepositoryScenarioSpec extends Specification with ScalaCheck { def is = s2"""
  a test for repository scenario $doit

"""

   def doit = prop((scenario: RepositoryScenario) => {
     println(scenario)
     false
   })
}
