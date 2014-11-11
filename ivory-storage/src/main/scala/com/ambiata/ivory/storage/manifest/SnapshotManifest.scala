package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import com.ambiata.ivory.storage.control._
import scalaz._, effect.IO

case class SnapshotManifest(core: Manifest, commit: CommitId, id: SnapshotId, data: SnapshotDataVersion, date: Date)

object SnapshotManifest {
  implicit def SnapshotManifestEqual: Equal[SnapshotManifest] =
    Equal.equalA[SnapshotManifest]

  implicit def SnapshotManifestCodecJson: CodecJson[SnapshotManifest] =
    casecodec5(SnapshotManifest.apply, SnapshotManifest.unapply)("metadata", "commit_id", "snapshot_id", "snapshot_data_version", "date")
}
