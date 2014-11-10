package com.ambiata.ivory.core

import argonaut._
import scalaz._


sealed trait MetadataVersion

object MetadataVersion {
  /** Nested json format */
  case object V1 extends MetadataVersion

  implicit def MetadataVersionEqual: Equal[MetadataVersion] =
    Equal.equalA[MetadataVersion]

  implicit def MetadataVersionCodecJson: CodecJson[MetadataVersion] =
    ArgonautPlus.codecEnum("MetadataVersion", {
      case V1 => "v1"
    }, {
      case "v1" => V1
    })
}
