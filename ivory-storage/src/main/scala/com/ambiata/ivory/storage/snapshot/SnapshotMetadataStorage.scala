package com.ambiata.ivory.storage.snapshot

import com.ambiata.ivory.core._
import com.ambiata.ivory.data._
import com.ambiata.mundane.control._
import com.ambiata.mundane.parse._
import com.ambiata.mundane.io._

import scalaz._, Scalaz._, effect.IO, \&/._

object SnapshotMetadataStorage {
  val MetadataFileName = FilePath(".snapmeta")

  def getIds(repository: Repository): ResultT[IO, List[SnapshotId]] = for {
    paths <- repository.toReference(Repository.snapshots).run(s => p => StoreDataUtil.listDir(s, p)).map(_.map(_.basename.path))
    ids   <- ResultT.fromOption[IO, List[SnapshotId]](paths.traverseU(SnapshotId.parse), "Could not parse snapshot id.")
  } yield ids

  def getById(repository: Repository, id: SnapshotId): ResultT[IO, SnapshotMetadata] = for {
    lines    <- repository.toReference(Repository.snapshots </> id.render.toFilePath </> MetadataFileName).run(store => store.linesUtf8.read)
    metadata <- ResultT.fromDisjunction[IO, SnapshotMetadata](parser(id).run(lines).disjunction.leftMap(This.apply))
  } yield metadata

  def getLatest(repository: Repository, date: Date): ResultT[IO, Option[SnapshotMetadata]] =
    list(repository).map(_.filter(_.date.isBeforeOrEqual(date)).sortBy(_.date).lastOption)

  def list(repository: Repository): ResultT[IO, List[SnapshotMetadata]] =
    getIds(repository).flatMap(_.traverseU(getById(repository, _)))

  def save(repository: Repository, metadata: SnapshotMetadata): ResultT[IO, Unit] =
    repository.toReference(Repository.snapshot(metadata.id) </> MetadataFileName).run(store => path => store.linesUtf8.write(path, render(metadata)))

  def parser(id: SnapshotId): ListParser[SnapshotMetadata] =  for {
    date  <- ListParser.localDate.map(Date.fromLocalDate)
    store <- FeatureStoreId.listParser
  } yield SnapshotMetadata(id, date, store)

  def render(metadata: SnapshotMetadata): List[String] = List(
    metadata.date.string("-")
  , metadata.storeId.render
  )
}
