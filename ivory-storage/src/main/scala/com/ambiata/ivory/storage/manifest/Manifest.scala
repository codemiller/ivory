package com.ambiata.ivory.storage.manifest

import com.ambiata.ivory.core._

case class Manifest[A](format: ManifestVersion, ivory: IvoryVersion, flavour: ManifestFlavour[A], timestamp: Long, detail: A)
