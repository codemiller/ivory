package com.ambiata.ivory.core

import scalaz._, Scalaz._

case class Snapshot(
  id: SnapshotId
, date: Date
, store: FeatureStore
, dictionary: Option[Identified[DictionaryId, Dictionary]]
, sized: Bytes \/ List[Sized[Namespace]]
, format: SnapshotFormat
) {
  def bytes: Bytes =
    sized.fold(identity, _.foldMap(_.bytes))

  def toMetadata: SnapshotMetadata =
    SnapshotMetadata(id, date, store.id, dictionary.map(_.id))
}
