package com.ambiata.ivory.core

sealed trait ManifestVersion

object ManifestVersion {
  case object V1 extends ManifestVersion
}
