package com.ambiata.ivory.core

import com.ambiata.ivory.core.arbitraries.Arbitraries._

import org.specs2._
import org.scalacheck._, Arbitrary._

import scalaz._, Scalaz._
import scalaz.scalacheck.ScalazProperties._


class EncodedAsSpec extends Specification with ScalaCheck { def is = s2"""

Laws
----

  Equal                                        ${equal.laws[EncodedAs]}


Combinators
-----------

  fromString/render symmetry:

     ${ prop((e: EncodedAs) => EncodedAs.fromString(e.render) ==== e.some) }

"""
}
