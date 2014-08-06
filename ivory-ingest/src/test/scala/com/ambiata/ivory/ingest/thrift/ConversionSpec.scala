package com.ambiata.ivory.ingest.thrift

import com.ambiata.ivory.core._, Arbitraries._
import org.joda.time.DateTimeZone
import org.specs2.{ScalaCheck, Specification}

class ConversionSpec extends Specification with ScalaCheck { def is = s2"""

Conversion
=========
   can convert a fact to and from thrift                     $conversion
   will return an error if the fact is missing               $invalid

"""

  import Conversion._

  def conversion = prop((se: SparseEntities) => {
    import se._
    thrift2fact(fact.namespace, fact2thrift(fact), zone, zone).toEither must beRight(fact)
  })

  def invalid = prop((tz: DateTimeZone) =>
    thrift2fact("", new ThriftFact, tz, tz).toEither must beLeft
  )
}
