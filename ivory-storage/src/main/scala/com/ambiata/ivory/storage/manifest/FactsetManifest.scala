package com.ambiata.ivory.storage.manifest

import argonaut._
import com.ambiata.ivory.core._

case class FactsetManifest(id: FactsetId, data: FactsetDataVersion)

object FactsetManifest {
  implicit def FactsetManifestEncodeJson: EncodeJson[FactsetManifest] =
    ???
}
