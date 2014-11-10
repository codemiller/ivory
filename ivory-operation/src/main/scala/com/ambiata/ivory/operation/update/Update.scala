package com.ambiata.ivory.operation.update

import com.ambiata.ivory.core._
import com.ambiata.ivory.storage.control._
import com.ambiata.ivory.storage.manifest._
import com.ambiata.ivory.storage.metadata._
import com.ambiata.ivory.storage.fact._
import scalaz._, Scalaz._

object Update {
  def update: RepositoryTIO[Unit] = RepositoryT.fromResultTIO(repository => for {
    store <- Metadata.latestFeatureStoreOrFail(repository)
    _ <- store.factsets.traverseU(f => for {
      glob <- FactsetGlob.select(repository, f.value.id)
      _ <- glob.traverseU(g => FactsetManifest.writeWith(repository, f.value.id, FactsetDataVersion.V2, g.partitions))
    } yield ())
  } yield ())
}
