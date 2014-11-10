package com.ambiata.ivory.core

import org.scalacheck._

object GenOutputFormat {
  def delimiter: Gen[Delimiter] =
    Gen.oneOf(Delimiter.Psv, Delimiter.Tsv, Delimiter.Csv)

  def encoding: Gen[EncodedAs] =
    Gen.oneOf(EncodedAs.Delimited, EncodedAs.Escaped)

  def form: Gen[Form] =
    Gen.oneOf(Form.Dense, Form.Sparse)

  def format: Gen[OutputFormat] = for {
    d <- delimiter
    e <- encoding
    f <- form
    o <- Gen.oneOf(OutputFormat.Text(f, d, e), OutputFormat.Thrift(f))
  } yield o
}
