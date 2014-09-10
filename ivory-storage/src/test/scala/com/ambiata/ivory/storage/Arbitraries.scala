package com.ambiata.ivory.storage

import com.ambiata.ivory.core._
import com.ambiata.ivory.data.Identifier
import com.ambiata.ivory.storage.TemporaryReferences.{Hdfs, S3, Posix, TemporaryType}
import com.ambiata.ivory.storage.fact._

import org.scalacheck._, Arbitrary._
import com.ambiata.ivory.core.Arbitraries._

import plan._

object Arbitraries {
  implicit def DatasetArbitrary: Arbitrary[Dataset] =
    Arbitrary(Gen.oneOf(arbitrary[Factset].map(FactsetDataset.apply), arbitrary[Snapshot].map(SnapshotDataset.apply)))

  implicit def FactsetVersionArbitrary: Arbitrary[FactsetVersion] =
    Arbitrary(Gen.oneOf(FactsetVersionOne, FactsetVersionTwo))

  implicit def StoreTypeArbitrary: Arbitrary[TemporaryType] =
    Arbitrary(Gen.oneOf(Posix, S3, Hdfs))
}
