package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import scalaz._


case class ChordOutputManifest(commit: CommitId, format: OutputFormat)

object ChordOutputManifest {
  implicit def ChordOutputManifestEqual: Equal[ChordOutputManifest] =
    Equal.equalA[ChordOutputManifest]

  implicit def ChordOutputManifestCodecJson: CodecJson[ChordOutputManifest] =
    casecodec2(ChordOutputManifest.apply, ChordOutputManifest.unapply)("commit_id", "format")
}
