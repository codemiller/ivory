package com.ambiata.ivory.core

import scalaz._, Scalaz._
import scala.{Ordering => SOrdering}

case class SnapshotMetadata(id: SnapshotId, date: Date, storeId: FeatureStoreId) {
  def order(other: SnapshotMetadata): Ordering =
    (id, date, storeId) ?|? ((other.id, other.date, other.storeId))
}

object SnapshotMetadata {
  implicit def SnapshotMetadataOrder: Order[SnapshotMetadata] =
    Order.order(_ order _)

  implicit def SnapshotMetadataOrdering: SOrdering[SnapshotMetadata] =
    SnapshotMetadataOrder.toScalaOrdering
}
