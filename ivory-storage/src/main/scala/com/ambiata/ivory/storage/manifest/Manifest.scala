package com.ambiata.ivory.storage.manifest

`import com.ambiata.ivory.storage.version._

sealed trait ManifestVersion
case object ManifestVersionV1 extends ManifestVersion

sealed trait ManifestFlavour[A]
case object SnapshotManifestFlavour extends ManifestFlavour[SnapshotManifest]
case object SnapshotOutputManifestFlavour extends ManifestFlavour[SnapshotOutputManifest]
case object ChordOutputManifestFlavour extends ManifestFlavour[ChordOutputManifest]
case object SquashManifestFlavour extends ManifestFlavour[SquashManifest]
case object FactsetManifestFlavour extends ManifestFlavour[FactsetManifest]

case class Manifest[A](format: ManifestVersion, ivory: IvoryVersion, flavour: ManifestFlavour[A], timestamp: Long, detail: A)


case class SnapshotManifest(id: SnapshotId, data: SnapshotDataVersion, date: Date, commit: CommitId \/ FeatureStoreId)
case class SnapshotOutputManifest(data: )
case class ChordOutputManifest()
case class SquashManifest()
case class FactsetManifest()
