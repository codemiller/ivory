package com.ambiata.ivory.storage.manifest

import argonaut._
import com.ambiata.ivory.core._

case class ChordOutputManifest(commit: CommitId, format: OutputFormat)

object ChordOutputManifest {
  implicit def ChordOutputManifestEncodeJson: EncodeJson[ChordOutputManifest] =
    ???

}
