package com.ambiata.ivory.operation.validation

import com.ambiata.ivory.core._, Arbitraries._
import com.ambiata.mundane.control.ResultT
import org.specs2._
import org.specs2.matcher.{ThrownExpectations, FileMatchers}
import scalaz.effect.IO

class ValidateSpec extends Specification with ThrownExpectations with FileMatchers with ScalaCheck { def is = s2"""

  Can validate with correct encoding                     $valid
  Can validate with incorrect encoding                   $invalid

  """

  def valid = prop((e: EncodingAndValue) =>
    Validate.validateEncoding(e.value, e.enc).toEither must beRight
  )

  def invalid = prop((e: EncodingAndValue, e2: Encoding) => (e.enc != e2 && !isCompatible(e, e2)) ==> {
    Validate.validateEncoding(e.value, e2).toEither must beLeft
  })

  // A small subset of  encoded values are valid for different optional/empty Structs/Lists
  private def isCompatible(e1: EncodingAndValue, e2: Encoding): Boolean =
    (e1, e2) match {
      case (EncodingAndValue(_, StructValue(m)), StructEncoding(v)) => m.isEmpty && v.forall(_._2.optional)
      case (EncodingAndValue(_, ListValue(l)), ListEncoding(e))     => l.forall(v => isCompatible(EncodingAndValue(e, v), e))
      case _ => false
    }
}
