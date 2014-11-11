package com.ambiata.ivory.core


/** This represents the version of the on-disk data that makes up a factset. */
sealed trait FactsetDataVersion

object FactsetDataVersion {
  /** V1 is a sequence file, with a null-key and a "thrift-fact" (i.e. no namespace / date component)
      value stored as bytes value, and the namespace / date encoded in the partition. */
  case object V1 extends FactsetDataVersion

  /** V2 is a sequence file, with a null-key and a "thrift-fact" (i.e. no namespace / date component)
      value stored as bytes value, and the namespace / date encoded in the partition.
      NOTE this is identical to V1 and was used to force out the potential to read
      multiple factset versions. */
  case object V2 extends FactsetDataVersion
}
