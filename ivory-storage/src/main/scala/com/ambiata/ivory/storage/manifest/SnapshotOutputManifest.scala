package com.ambiata.ivory.storage.manifest

import argonaut._
import com.ambiata.ivory.core._

case class SnapshotOutputManifest(commit: CommitId, snapshot: SnapshotId, format: OutputFormat)

object SnapshotOutputManifest {
  implicit def SnapshotOutputManifestEncodeJson: EncodeJson[SnapshotOutputManifest] =
    ???
}
