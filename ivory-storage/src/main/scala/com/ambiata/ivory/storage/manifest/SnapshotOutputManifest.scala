package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import scalaz._

case class SnapshotOutputManifest(core: Manifest, commit: CommitId, snapshot: SnapshotId, format: OutputFormat)

object SnapshotOutputManifest {
  implicit def SnapshotOutputManifestEqual: Equal[SnapshotOutputManifest] =
    Equal.equalA[SnapshotOutputManifest]

  implicit def SnapshotOutputManifestCodecJson: CodecJson[SnapshotOutputManifest] =
    casecodec4(SnapshotOutputManifest.apply, SnapshotOutputManifest.unapply)("metadata", "commit_id", "snapshot_id", "output_format")
}
