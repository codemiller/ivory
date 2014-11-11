package com.ambiata.ivory.core

import argonaut._
import scalaz._


sealed trait ManifestVersion

object ManifestVersion {
  /** Nested json format */
  case object V1 extends ManifestVersion

  implicit def ManifestVersionEqual: Equal[ManifestVersion] =
    Equal.equalA[ManifestVersion]

  implicit def ManifestVersionCodecJson: CodecJson[ManifestVersion] =
    ArgonautPlus.codecEnum("ManifestVersion", {
      case V1 => "v1"
    }, {
      case "v1" => V1
    })
}
