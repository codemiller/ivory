package com.ambiata.ivory.core

import scalaz._, Scalaz._

case class Snapshot(
  id: SnapshotId
, date: Date
, store: FeatureStore
, dictionary: Option[Identified[DictionaryId, Dictionary]]
, bytes: Bytes \/ List[Sized[Namespace]]
, format: SnapshotFormat
) {
  def toMetadata: SnapshotMetadata =
    SnapshotMetadata(id, date, store.id, dictionary.map(_.id))

  def totalBytes: Bytes =
    bytes match {
      case -\/(b)  => b
      case \/-(bs) => bs.foldMap(_.bytes)
    }
}
