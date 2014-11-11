package com.ambiata.ivory.storage.manifest

import com.ambiata.ivory.core._
import com.ambiata.ivory.core.ArgonautProperties._
import com.ambiata.ivory.core.arbitraries.Arbitraries._

import org.specs2._
import org.scalacheck._, Arbitrary._

import scalaz._, Scalaz._
import scalaz.scalacheck.ScalazProperties._
import scalaz.scalacheck.ScalaCheckBinding._


class SnapshotOutputManifestSpec extends Specification with ScalaCheck { def is = s2"""

Laws
----

  Encode/Decode Json                           ${encodedecode[SnapshotOutputManifest]}
  Equal                                        ${equal.laws[SnapshotOutputManifest]}

"""
  implicit def SnapshotOutputManifestArbitrary: Arbitrary[SnapshotOutputManifest] =
    Arbitrary((arbitrary[CommitId] |@|
               arbitrary[SnapshotId] |@|
               arbitrary[OutputFormat])(SnapshotOutputManifest.apply))
}
