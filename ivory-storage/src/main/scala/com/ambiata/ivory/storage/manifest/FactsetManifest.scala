package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import scalaz._


case class FactsetManifest(core: Manifest, id: FactsetId, data: FactsetDataVersion, partitions: List[Partition])

object FactsetManifest {
  def location(repository: Repository, id: FactsetId): IvoryLocation =
    repository.toIvoryLocation(Repository.factset(id)) </> FileName.unsafe(".manifest.json")

  def writeWith(repository: Repository, id: FactsetId, data: FactsetDataVersion, partitions: List[Partition]): ResultTIO[Unit] =
    write(repository, FactsetManifest(Manifest(ManifestVersion.V1, IvoryVersion.get), id, data, partitions))

  def write(repository: Repository, manifest: FactsetManifest): ResultTIO[Unit] =
    IvoryLocation.writeUtf8(location(repository, manifest.id), manifest.asJson.spaces2)

  def read(repository: Repository, id: FactsetId): ResultTIO[Option[FactsetManifest]] =
    IvoryLocation.readUtf8(location(repository, id)).map(s => s.decodeOption[FactsetManifest])

  def readOrFail(repository: Repository, id: FactsetId): ResultTIO[FactsetManifest] =
    IvoryLocation.readUtf8(location(repository, id)).flatMap(s => ResultT.fromDisjunctionString(s.decodeEither[FactsetManifest]))

  def exists(repository: Repository, id: FactsetId): ResultTIO[Boolean] =
    IvoryLocation.exists(location(repository, id))

  implicit def FactsetManifestEqual: Equal[FactsetManifest] =
    Equal.equalA[FactsetManifest]

  implicit def FactsetManifestCodecJson: CodecJson[FactsetManifest] =
    casecodec4(FactsetManifest.apply, FactsetManifest.unapply)("metadata", "factset_id", "factset_data_version", "partitions")
}
