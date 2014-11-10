package com.ambiata.ivory.core

import argonaut._, Argonaut._
import scalaz._, Scalaz._

sealed trait OutputFormat {
  import OutputFormat._

  def fold[X](
    text: (Form, Delimiter, EncodedAs) => X
  , thrift: Form => X
  ): X = this match {
    case Text(form, delimiter, encoding) =>
      text(form, delimiter, encoding)
    case Thrift(form) =>
      thrift(form)
  }

  def isText: Boolean =
    fold((_, _, _) => true, _ => false)

  def isThrift: Boolean =
    fold((_, _, _) => false, _ => true)

  def render: String =
    fold(
      (form, delimiter, encoding) => s"${form.render}:${encoding.render}:${delimiter.render}"
    , form => s"${form.render}:thrift"
    )

}

object OutputFormat {
  case class Text(form: Form, delimiter: Delimiter, encoding: EncodedAs) extends OutputFormat
  case class Thrift(form: Form) extends OutputFormat

  def text(form: Form, delimiter: Delimiter, encoding: EncodedAs): OutputFormat =
    Text(form, delimiter, encoding)

  def thrift(form: Form): OutputFormat =
    Thrift(form)

  def fromString(s: String): Option[OutputFormat] =
    s.split(":").toList match {
      case Form(form) :: EncodedAs(encoding) :: Delimiter(delimiter) :: Nil =>
        Text(form, delimiter, encoding).some
      case Form(form) :: Delimiter(delimiter) :: Nil =>
        Text(form, delimiter, EncodedAs.Delimited).some
      case Form(form) :: "thrift" :: Nil =>
        Thrift(form).some
      case _ =>
        none
    }

  implicit def OutputFormatEqual: Equal[OutputFormat] =
    Equal.equalA[OutputFormat]

  implicit def OutputFormatEncodeJson: EncodeJson[OutputFormat] =
    EncodeJson(_.render.asJson)

  implicit def OutputFormatDecodeJson: DecodeJson[OutputFormat] =
    DecodeJson.optionDecoder(_.string >>= fromString, "OutputFormat")
}
