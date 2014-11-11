package com.ambiata.ivory.core

/** This represents the version of the on-disk data that makes up an internal snapshot. */
sealed trait SnapshotDataVersion

object SnapshotDataVersion {
  /** V1 is a sequence file, with a null-key and a "namespaced-thrift-fact" value stored as
      bytes value with no partitioning. */
  case object V1 extends SnapshotDataVersion
}
