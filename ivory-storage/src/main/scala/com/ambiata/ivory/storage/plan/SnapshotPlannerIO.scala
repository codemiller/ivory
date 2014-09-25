package com.ambiata.ivory.storage.plan

import com.ambiata.ivory.core._
import com.ambiata.mundane.control._
import scalaz._, effect.IO

object SnapshotPlannerIO {
  def plan(repository: Repository, date: Date): ResultT[IO, Datasets] =
    ???
}
