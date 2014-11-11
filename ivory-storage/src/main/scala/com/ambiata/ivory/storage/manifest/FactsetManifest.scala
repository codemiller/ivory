package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import scalaz._


case class FactsetManifest(core: Manifest, id: FactsetId, data: FactsetDataVersion)

object FactsetManifest {
  implicit def FactsetManifestEqual: Equal[FactsetManifest] =
    Equal.equalA[FactsetManifest]

  implicit def FactsetManifestCodecJson: CodecJson[FactsetManifest] =
    casecodec3(FactsetManifest.apply, FactsetManifest.unapply)("metadata", "factset_id", "factset_data_version")
}
