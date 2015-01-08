package com.ambiata.ivory.core

import argonaut._
import scalaz._


/** This represents the version of the on-disk data that makes up an internal snapshot. */
sealed trait SnapshotFormat

object SnapshotFormat {
  /** V1 is a sequence file, with a null-key and a "namespaced-thrift-fact" value stored as
      bytes value with no partitioning. */
  case object V1 extends SnapshotFormat

  /** V2 is partitioned sequence files by namespace. The key is the fact date stored as an
      integer, and the value is the "thrift-fact" stored as bytes. */
  case object V2 extends SnapshotFormat

  /* NOTE Don't forget the arbitrary if you add a format. */

  implicit def SnapshotFormatEqual: Equal[SnapshotFormat] =
    Equal.equalA[SnapshotFormat]

  implicit def SnapshotFormatCodecJson: CodecJson[SnapshotFormat] =
    ArgonautPlus.codecEnum("SnapshotFormat", {
      case V1 => "v1"
      case V2 => "v2"
    }, {
      case "v1" => V1
      case "v2" => V2
    })
}
