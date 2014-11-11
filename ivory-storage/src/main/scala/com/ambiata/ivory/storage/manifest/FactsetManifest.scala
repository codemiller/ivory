package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import scalaz._


case class FactsetManifest(id: FactsetId, data: FactsetDataVersion)

object FactsetManifest {
  implicit def FactsetManifestEqual: Equal[FactsetManifest] =
    Equal.equalA[FactsetManifest]

  implicit def FactsetManifestCodecJson: CodecJson[FactsetManifest] =
    casecodec2(FactsetManifest.apply, FactsetManifest.unapply)("factset_id", "factset_data_version")
}
