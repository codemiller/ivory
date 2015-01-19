package com.ambiata.ivory.core

import org.specs2._
import com.ambiata.ivory.core.arbitraries.Arbitraries._

class SnapshotSpec extends Specification with ScalaCheck { def is = s2"""

Snapshot
--------

  Can create SnapshotMetadata from Snapshot          ${metadata}

SnapshotInfo
------------

  Can get location keys from snapshot                ${location}

"""

  def metadata = prop((s: Snapshot) =>
    s.toMetadata ==== SnapshotMetadata(s.id, s.date, s.store.id, s.dictionary.map(_.id)))

  def location = prop((snapshot: Snapshot) =>
    snapshot.location ==== (snapshot.info match {
      case SnapshotInfoV1(b)  => List(Repository.snapshot(snapshot.id))
      case SnapshotInfoV2(bs) => bs.map(Repository.snapshot(snapshot.id) / _.value.asKeyName)
    }))
}
