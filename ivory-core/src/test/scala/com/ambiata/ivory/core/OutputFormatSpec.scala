package com.ambiata.ivory.core

import com.ambiata.ivory.core.ArgonautProperties._
import com.ambiata.ivory.core.arbitraries.Arbitraries._

import org.specs2._
import org.scalacheck._, Arbitrary._

import scalaz._, Scalaz._
import scalaz.scalacheck.ScalazProperties._


class OutputFormatSpec extends Specification with ScalaCheck { def is = s2"""

Laws
----

  Encode/Decode Json                           ${encodedecode[OutputFormat]}
  Equal                                        ${equal.laws[OutputFormat]}


Combinators
-----------

  OutputFormat.Text fold only evalutes text expression:

     ${ prop((f: Form, d: Delimiter, e: EncodedAs) =>
          OutputFormat.text(f, d, e).fold((ff, dd, ee) => (ff, dd, ee), _ => ???) ==== ((f, d, e))) }

  OutputFormat.Thrift fold only evalutes thrift expression:

     ${ prop((f: Form) =>
          OutputFormat.thrift(f).fold((_, _, _) => ???, ff => ff) ==== f) }

  Fold constructors is identity:

     ${ prop((o: OutputFormat) =>
         o.fold((f, d, e) => OutputFormat.text(f, d, e), f => OutputFormat.thrift(f)) ==== o) }

  fromString/render symmetry:

     ${ prop((o: OutputFormat) => OutputFormat.fromString(o.render) ==== o.some) }

   isText is true iff it is indeed text output format:

     ${ prop((f: Form, d: Delimiter, e: EncodedAs) => OutputFormat.text(f, d, e).isText ==== true) }
     ${ prop((f: Form) => OutputFormat.thrift(f).isText ==== false) }

   isThrift is true iff it is indeed thrift output format:

     ${ prop((f: Form) => OutputFormat.thrift(f).isThrift ==== true) }
     ${ prop((f: Form, d: Delimiter, e: EncodedAs) => OutputFormat.text(f, d, e).isThrift ==== false) }

  isThrift/isText are exclusive:

     ${ prop((o: OutputFormat) => o.isText ^ o.isThrift) }

Constructors
------------

   Lower-case constructors are just alias with the right type:

     ${ prop((f: Form, d: Delimiter, e: EncodedAs) => OutputFormat.text(f, d, e) ==== OutputFormat.Text(f, d, e)) }
     ${ prop((f: Form) => OutputFormat.thrift(f) ==== OutputFormat.Thrift(f)) }

"""
}
