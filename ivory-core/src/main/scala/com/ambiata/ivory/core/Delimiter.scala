package com.ambiata.ivory.core

sealed abstract class Delimiter(val name: String, val character: Char) {
  def render: String =
    name
}

object Delimiter {
  case object Psv extends Delimiter("psv", '|')
  case object Csv extends Delimiter("csv", ',')
  case object Tsv extends Delimiter("tsv", '\t')
}
