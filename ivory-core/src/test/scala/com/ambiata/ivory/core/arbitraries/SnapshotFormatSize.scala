package com.ambiata.ivory.core.arbitraries

import com.ambiata.ivory.core._
import com.ambiata.ivory.core.gen._

import org.scalacheck._

import scalaz._

case class SnapshotFormatSize(format: SnapshotFormat, bytes: Bytes \/ List[Sized[Namespace]])

object SnapshotFormatSize {
  implicit def SnapshotFormatSizeArbitrary: Arbitrary[SnapshotFormatSize] =
    Arbitrary(GenRepository.snapshotFormatSize.map((SnapshotFormatSize.apply _).tupled))
}

