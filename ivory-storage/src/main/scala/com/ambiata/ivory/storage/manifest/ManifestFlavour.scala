package com.ambiata.ivory.storage.manifest

sealed trait ManifestFlavour[A]

object ManifestFlavour {
  case object Snapshot extends ManifestFlavour[SnapshotManifest]
  case object SnapshotOutput extends ManifestFlavour[SnapshotOutputManifest]
  case object ChordOutput extends ManifestFlavour[ChordOutputManifest]
  case object Factset extends ManifestFlavour[FactsetManifest]
}
