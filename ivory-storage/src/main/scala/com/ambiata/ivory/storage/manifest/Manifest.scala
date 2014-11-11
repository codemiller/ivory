package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._

case class Manifest(format: ManifestVersion, ivory: IvoryVersion)

object Manifest {
  implicit def ManifestCodecJson: CodecJson[Manifest] =
    casecodec2(Manifest.apply, Manifest.unapply)("manifest_version", "ivory_version")


//  def save[A: EncodeJson](manifest: Manifest[A], location: IvoryLocation): ResultT[IO, Unit] =
//    IvoryLocation.writeUtf8(location, manifest.asJson.spaces2)
}
