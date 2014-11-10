package com.ambiata.ivory.storage.manifest

case class IvoryVersion(version: String)

sealed trait ManifestVersion
case object ManifestVersionV1 extends ManifestVersion

case class Manifest[A](format: ManifestVersion, ivory: IvoryVersion)
