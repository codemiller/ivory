package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import scalaz._

case class GeneralManifest(format: MetadataVersion, ivory: IvoryVersion)

object GeneralManifest {

  implicit def GeneralManifestEqual: Equal[GeneralManifest] =
    Equal.equalA

  implicit def GeneralManifestCodecJson: CodecJson[GeneralManifest] =
    casecodec2(GeneralManifest.apply, GeneralManifest.unapply)("metadata_version", "ivory_version")
}
