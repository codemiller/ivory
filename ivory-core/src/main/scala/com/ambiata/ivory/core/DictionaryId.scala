package com.ambiata.ivory.core

import scalaz._, Scalaz._
import scala.math.{Ordering => SOrdering}

// TODO: Convert to Identifier
case class DictionaryId(id: String) {
  def render = id
  // def next = id.next.map(DictionaryId.apply)
  def order(other: DictionaryId): Ordering =
    id ?|? other.id
}

object DictionaryId {
  implicit def DictionaryIdOrder: Order[DictionaryId] =
    Order.order(_ order _)

  implicit def DictionaryIdOrdering =
    DictionaryIdOrder.toScalaOrdering

  // def initial: DictionaryId =
  //   DictionaryId(Identifier.initial)

  // def parse(strId: String): Option[DictionaryId] =
  //   Identifier.parse(strId).map(DictionaryId.apply)
}