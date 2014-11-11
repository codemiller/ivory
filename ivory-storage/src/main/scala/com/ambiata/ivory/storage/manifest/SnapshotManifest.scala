package com.ambiata.ivory.storage.manifest

import com.ambiata.ivory.core._
import scalaz._

case class SnapshotManifest(id: SnapshotId, data: SnapshotDataVersion, date: Date, commit: CommitId \/ FeatureStoreId)
