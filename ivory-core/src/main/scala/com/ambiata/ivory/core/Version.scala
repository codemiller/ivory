package com.ambiata.ivory.core

import com.ambiata.ivory.reflect.MacrosCompat
import com.ambiata.notion.core.KeyName
import com.ambiata.mundane.parse.ListParser
import scalaz._, Scalaz._
import argonaut._, Argonaut._

case class Version private (toInt: Int) {
  def render: String =
    s"v${toInt}"

  def next: Option[Version] =
    (toInt != Int.MaxValue).option(Version(toInt + 1))

  def order(i: Version): Ordering =
    (toInt ?|? i.toInt)
}

object Version extends MacrosCompat {
  def initial: Version =
    Version(1)

  def parse(s: String): Option[Version] =
    s.startsWith("v").option(s).flatMap(_.tail.parseInt.toOption).filter(_ > 0).map(new Version(_))

  implicit def VersionOrder: Order[Version] =
    Order.order(_ order _)

  implicit def VersionOrdering: scala.Ordering[Version] =
    VersionOrder.toScalaOrdering

  implicit def VersionEncodeJson: EncodeJson[Version] =
    EncodeJson(_.render.asJson)

  implicit def VersionDecodeJson: DecodeJson[Version] =
    DecodeJson.optionDecoder(_.string.flatMap(parse), "Version")

  def apply(string: String): Version =
    macro versionMacro

  def versionMacro(c: Context)(string: c.Expr[String]): c.Expr[Version] = {
    import c.universe._
    string match {
      case Expr(Literal(Constant(str: String))) =>
        Version.parse(str).getOrElse(c.abort(c.enclosingPosition, s"Invalid Version: $str"))
        c.Expr[Version](q"Version.parse($str).get")

      case other =>
        c.abort(c.enclosingPosition, s"This is not a valid Version string: ${showRaw(string)}")
    }
  }
}
