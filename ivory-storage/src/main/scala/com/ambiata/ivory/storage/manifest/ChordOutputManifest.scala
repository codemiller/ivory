package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import scalaz._


case class ChordOutputManifest(core: Manifest, commit: CommitId, format: OutputFormat)

object ChordOutputManifest {
  implicit def ChordOutputManifestEqual: Equal[ChordOutputManifest] =
    Equal.equalA[ChordOutputManifest]

  implicit def ChordOutputManifestCodecJson: CodecJson[ChordOutputManifest] =
    casecodec3(ChordOutputManifest.apply, ChordOutputManifest.unapply)("metadata", "commit_id", "output_format")
}
