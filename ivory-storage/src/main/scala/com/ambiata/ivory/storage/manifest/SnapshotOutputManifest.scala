package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import scalaz._

case class SnapshotOutputManifest(commit: CommitId, snapshot: SnapshotId, format: OutputFormat)

object SnapshotOutputManifest {
  implicit def SnapshotOutputManifestEqual: Equal[SnapshotOutputManifest] =
    Equal.equalA[SnapshotOutputManifest]

  implicit def SnapshotOutputManifestCodecJson: CodecJson[SnapshotOutputManifest] =
    casecodec3(SnapshotOutputManifest.apply, SnapshotOutputManifest.unapply)("commit_id", "snapshot_id", "format")
}
