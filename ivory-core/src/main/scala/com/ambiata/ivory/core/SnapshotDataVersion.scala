package com.ambiata.ivory.core

import argonaut._, Argonaut._
import scalaz._, Scalaz._

/** This represents the version of the on-disk data that makes up an internal snapshot. */
sealed trait SnapshotDataVersion

object SnapshotDataVersion {
  /** V1 is a sequence file, with a null-key and a "namespaced-thrift-fact" value stored as
      bytes value with no partitioning. */
  case object V1 extends SnapshotDataVersion

  implicit def SnapshotDataVersionEqual: Equal[SnapshotDataVersion] =
    Equal.equalA[SnapshotDataVersion]

  implicit def SnapshotDataVersionEncodeJson: EncodeJson[SnapshotDataVersion] =
    EncodeJson({ case V1 => "v1".asJson })

  implicit def SnapshotDataVersionDecodeJson: DecodeJson[SnapshotDataVersion] =
    DecodeJson.optionDecoder(_.string.flatMap({
      case "v1" =>
        V1.some
      case _ =>
        none
    }), "SnapshotDataVersion")
}
