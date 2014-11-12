package com.ambiata.ivory.storage.manifest

import argonaut._, Argonaut._
import com.ambiata.ivory.core._
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import scalaz._


case class FactsetManifest(core: Manifest, id: FactsetId, data: FactsetDataVersion, partitions: List[Partition])

object FactsetManifest {
  def writeWith(repository: Repository, id: FactsetId, data: FactsetDataVersion, partitions: List[Partition]): ResultTIO[Unit] =
    IvoryLocation.writeUtf8(repository.toIvoryLocation(Repository.factset(id)) </> FileName.unsafe(".manifest.json"),
      FactsetManifest(Manifest(ManifestVersion.V1, IvoryVersion.get), id, data, partitions).asJson.spaces2)

  implicit def FactsetManifestEqual: Equal[FactsetManifest] =
    Equal.equalA[FactsetManifest]

  implicit def FactsetManifestCodecJson: CodecJson[FactsetManifest] =
    casecodec4(FactsetManifest.apply, FactsetManifest.unapply)("metadata", "factset_id", "factset_data_version", "partitions")
}
