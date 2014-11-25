package com.ambiata.ivory.core

import argonaut._, Argonaut._
import scalaz._, Scalaz._

sealed trait OutputFormat {
  def render: String = this match {
    case DenseFormat(DelimitedFile(delimiter)) =>
      s"dense:${delimiter.render}"
    case SparseFormat(DelimitedFile(delimiter)) =>
      s"sparse:${delimiter.render}"
    case DenseFormat(ThriftFile) =>
      s"dense:thrift"
    case SparseFormat(ThriftFile) =>
      s"sparse:thrift"
  }

  def format: OutputFileFormat = this match {
    case DenseFormat(f) => f
    case SparseFormat(f) => f
  }
}
case class DenseFormat(file: OutputFileFormat) extends OutputFormat
case class SparseFormat(file: OutputFileFormat) extends OutputFormat

sealed trait OutputFileFormat
case class DelimitedFile(delim: Delimiter) extends OutputFileFormat
case object ThriftFile extends OutputFileFormat

object OutputFormat {
  def fromString(s: String): Option[OutputFormat] = PartialFunction.condOpt(s)({
    case "dense:psv"      => DenseFormat(DelimitedFile(Delimiter.Psv))
    case "dense:csv"      => DenseFormat(DelimitedFile(Delimiter.Csv))
    case "dense:tsv"      => DenseFormat(DelimitedFile(Delimiter.Tsv))
    case "dense:thrift"   => DenseFormat(ThriftFile)
    case "sparse:psv"     => SparseFormat(DelimitedFile(Delimiter.Psv))
    case "sparse:csv"     => SparseFormat(DelimitedFile(Delimiter.Csv))
    case "sparse:tsv"     => SparseFormat(DelimitedFile(Delimiter.Tsv))
    case "sparse:thrift"  => SparseFormat(ThriftFile)
  })

  implicit def OutputFormatEqual: Equal[OutputFormat] =
    Equal.equalA[OutputFormat]

  implicit def OutputFormatEncodeJson: EncodeJson[OutputFormat] =
    EncodeJson(_.render.asJson)

  implicit def OutputFormatDecodeJson: DecodeJson[OutputFormat] =
    DecodeJson.optionDecoder(_.string >>= fromString, "OutputFormat")
}
