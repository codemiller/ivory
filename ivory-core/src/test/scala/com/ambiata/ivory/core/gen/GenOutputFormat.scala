package com.ambiata.ivory.core

import org.scalacheck._

object GenOutputFormat {
  def delimiter: Gen[Delimiter] =
    Gen.oneOf(Delimiter.Psv, Delimiter.Tsv, Delimiter.Csv)

  def format: Gen[OutputFormat] = for {
    d <- delimiter
    f <- Gen.oneOf(DenseFormat(d), SparseFormat(d))
  } yield f
}
