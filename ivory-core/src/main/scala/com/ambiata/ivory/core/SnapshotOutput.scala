package com.ambiata.ivory.core

import com.ambiata.notion.core._

import scalaz._

case class SnapshotOutput(snapshot: Snapshot) {
  def location(repository: Repository): List[IvoryLocation] = {
    val base: Key = Repository.snapshot(snapshot.id)
    snapshot.bytes match {
      case -\/(_)  => List(repository.toIvoryLocation(base))
      case \/-(bs) => bs.map(s => repository.toIvoryLocation(base / s.value.asKeyName))
    }
  }
}
