package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import scalaz._

case class SnapshotManifest(commit: CommitId, id: SnapshotId, data: SnapshotDataVersion, date: Date)

object SnapshotManifest {
  implicit def SnapshotManifestEqual: Equal[SnapshotManifest] =
    Equal.equalA[SnapshotManifest]

  implicit def SnapshotManifestCodecJson: CodecJson[SnapshotManifest] =
    casecodec4(SnapshotManifest.apply, SnapshotManifest.unapply)("commit_id", "snapshot_id", "snapshot_data_version", "date")
}
