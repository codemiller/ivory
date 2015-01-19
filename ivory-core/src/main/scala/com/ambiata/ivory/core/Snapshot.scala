package com.ambiata.ivory.core

import com.ambiata.notion.core.Key

import scalaz._, Scalaz._

case class Snapshot(
  id: SnapshotId
, date: Date
, store: FeatureStore
, dictionary: Option[Identified[DictionaryId, Dictionary]]
, info: SnapshotInfo
) {
  def toMetadata: SnapshotMetadata =
    SnapshotMetadata(id, date, store.id, dictionary.map(_.id))

  def location: List[Key] = {
    val base = Repository.snapshot(id)
    info match {
      case SnapshotInfoV1(_)     => List(base)
      case SnapshotInfoV2(bytes) => bytes.map(s => base / s.value.asKeyName)
    }
  }
}

sealed trait SnapshotInfo {
  def totalBytes: Bytes
  def format: SnapshotFormat
}
case class SnapshotInfoV1(totalBytes: Bytes) extends SnapshotInfo {
  val format = SnapshotFormat.V1
}
case class SnapshotInfoV2(bytes: List[Sized[Namespace]]) extends SnapshotInfo {
  val format = SnapshotFormat.V2
  override def totalBytes: Bytes =
    bytes.foldMap(_.bytes)
}
