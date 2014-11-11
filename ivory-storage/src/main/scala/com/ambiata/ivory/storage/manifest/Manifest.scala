package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import com.ambiata.mundane.control._
import scalaz.effect.IO

case class Manifest[A](format: ManifestVersion, ivory: IvoryVersion, flavour: ManifestFlavour[A], detail: A)

object Manifest {
  implicit def ManifestEncodeJson[A: EncodeJson]: EncodeJson[Manifest[A]] =
    ???

  def save[A: EncodeJson](manifest: Manifest[A], location: IvoryLocation): ResultT[IO, Unit] =
    IvoryLocation.writeUtf8(location, manifest.asJson.spaces2)
}
