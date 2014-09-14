package com.ambiata.ivory.operation.extraction

import org.apache.commons.logging.LogFactory

import com.ambiata.ivory.core._, IvorySyntax._
import com.ambiata.ivory.storage.legacy._
import com.ambiata.ivory.storage.metadata.Metadata._
import com.ambiata.ivory.storage.metadata._
import com.ambiata.ivory.storage.snapshot._
import com.ambiata.ivory.storage.plan._
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import com.ambiata.poacher.hdfs._
import org.apache.hadoop.fs.Path

import scala.math.{Ordering => SOrdering}
import scalaz.{DList => _, _}, effect._

/**
 * Snapshots are used to store the latest feature values at a given date.
 * This can be done incrementally in order to be fast.
 *
 * Snapshots are also used to extract chords and pivots.
 *
 * After each snapshot is taken, metadata is saved to know exactly:
 *
 *  - when the snapshot was taken
 *  - on which FeatureStore it was taken
 *
 *
 * Note that in between 2 snapshots, the FeatureStore might have changed
 */
object Snapshots {
  /**
   * Take a new snapshot
   * If incremental is true, take a incremental snapshot (based off the previous one), unless the previous one is up to date
   */
  def takeSnapshot(repository: Repository, date: Date): ResultTIO[SnapshotMetadata] =
    SnapshotMetadataStorage.getLatest(repository, date).flatMap({
      case Some(m) if m.date == date =>
        ResultT.safe[IO, SnapshotMetadata](m)
      case _ =>
        createSnapshot(repository, date)
    })

  /**
   * create a new snapshot at a given date, using the previous snapshot data if present
   */
  def createSnapshot(repository: Repository, date: Date): ResultTIO[SnapshotMetadata] =
    for {
      newSnapshot <- SnapshotMeta.createSnapshotMetadata(repository, date)
      output      =  repository.toReference(Repository.snapshot(newSnapshot.id))
      _           <- runSnapshot(repository, newSnapshot, date, output)
    } yield newSnapshot

  /**
   * Run a snapshot on a given repository using the previous snapshot in case of an incremental snapshot
   */
  def runSnapshot(repository: Repository, newSnapshot: SnapshotMetadata, date: Date, output: ReferenceIO): ResultTIO[Unit] =
    for {
      hr              <- downcast[Repository, HdfsRepository](repository, s"Snapshot only works with Hdfs repositories currently, got '$repository'")
      outputStore     <- downcast[Any, HdfsStore](output.store, s"Snapshot output must be on HDFS, got '$output'")
      out             =  (outputStore.base </> output.path).toHdfs
      dictionary      <- dictionaryFromIvory(repository)
      datasets        <- SnapshotPlanner.plan(repository, date)
      _               <- SnapshotJob.run(hr.root.toHdfs, datasets, date, out, hr.codec).run(hr.configuration)
      // FIX WTF!?
      _               <- DictionaryTextStorageV2.toStore(output </> FilePath(".dictionary"), dictionary)
      _               <- SnapshotMetadataStorage.save(repository, newSnapshot)
    } yield ()
}
