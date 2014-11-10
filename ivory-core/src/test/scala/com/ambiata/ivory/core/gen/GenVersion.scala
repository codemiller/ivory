package com.ambiata.ivory.core

import org.scalacheck._

object GenVersion {
  def snapshot: Gen[SnapshotDataVersion] =
    Gen.const(SnapshotDataVersion.V1)

  def factset: Gen[FactsetDataVersion] =
    Gen.oneOf(FactsetDataVersion.V1, FactsetDataVersion.V2)

  def metadata: Gen[MetadataVersion] =
    Gen.const(MetadataVersion.V1)
}
