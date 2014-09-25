package com.ambiata.ivory.core

import Arbitraries._

import org.scalacheck._, Arbitrary._

import scalaz.{Name => _, _}, Scalaz._, scalacheck.ScalaCheckBinding._

/**
 * Represents a somewhat realistic set of repository metadata.
 *
 * Effectively you end up with a feature store that has been built up over
 * a series of days from 'start' to 'end'. At each day there may have been
 * new data ingested and/or a snapshot taken as at the current date.
 */
case class RepositoryScenario(store: FeatureStore, snapshots: List[Snapshot], epoch: Date, at: Date) {
  def metadata: List[SnapshotMetadata] =
    snapshots.map(snapshot => SnapshotMetadata(snapshot.id, snapshot.date, snapshot.store.id))

  def partitions: List[Partition] =
    store.factsets.flatMap(_.value.partitions).sorted
}

object RepositoryScenario {
  implicit def RepositoryScenarioArbitrary: Arbitrary[RepositoryScenario] = Arbitrary(for {
      // We pick some date to start the repository at
      epoch  <- genDate(Date(1900, 1, 1), Date(2100, 12, 31))

      // We will be generating 'span' days with of events (ingestions and/or snapshots).
      span    <- Gen.choose(2, 5)

      // The repository will contain 'n' different namespaces.
      n       <- Gen.choose(2, 4)
      names   <- Gen.listOfN(n, arbitrary[Name])

      // Work out if we want the snapshot 'at' date to be in the future, past, or mid-repository
      delta   <- Gen.choose(1, span)
      at      <- Gen.frequency(
          2 -> takeDays(epoch, delta)
        , 6 -> addDays(epoch, delta)
        , 2 -> addDays(epoch, span + delta)
        )

      // We start with an empty repository and will iterate each day until we end up with a complete view
      init    =  RepositoryScenario(FeatureStore(FeatureStoreId.initial, Nil), Nil, epoch, at)

      // For each day, take the current feature store, and then:
      //  - calculate a new factset for 'ingestion' using our determined namespaces
      //  - potentiolly generate a snapshot for today (1 out of 2 chance)
      //  - increment the state of the current scenario to track the new snapshot and head of the feature store
      r       <- (1 to span).toList.foldLeftM(init)((acc, day) => for {
        chance    <- Gen.choose(1, 10).map(_ < 5)
        today     =  addDays(acc.epoch, day)
        store     =  nextStore(acc.store, names, today)
        snapshots =  nextSnapshots(store, acc.snapshots, today, chance)
      } yield RepositoryScenario(store, snapshots, acc.epoch, acc.at))
    } yield r)

  def nextSnapshots(store: FeatureStore, snapshots: List[Snapshot], today: Date, create: Boolean) = {
    val current = if (snapshots.isEmpty) SnapshotId.initial.some else snapshots.map(_.id).maximum
    val next = current.flatMap(_.next)
    snapshots ++ next.map(id => Snapshot(id, today, store)).filter(_ => create).toList
  }

  def nextStore(store: FeatureStore, names: List[Name], today: Date): FeatureStore = (for {
    id        <- store.id.next
    priority  <- if (store.factsets.isEmpty) Priority.Min.some else store.factsets.map(_.priority).maximum.flatMap(_.next)
    factsetId <- if (store.factsets.isEmpty) FactsetId.initial.some else store.factsets.map(_.value.id).maximum.flatMap(_.next)
    factset   =  Factset(factsetId, for {
                   name <- names
                   date <- List(today, takeDays(today, 1), addDays(today, 1))
                 } yield Partition(name, date))
    nu        =  List(Prioritized(priority, factset))
  } yield FeatureStore(id, store.factsets ++ nu)).getOrElse(store)

  def addDays(date: Date, days: Int): Date =
    Date.fromLocalDate(date.localDate.plusDays(days))

  def takeDays(date: Date, days: Int): Date =
    Date.fromLocalDate(date.localDate.minusDays(days))

}
