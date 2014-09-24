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
case class RepositoryScenario(store: FeatureStore, snapshots: List[Snapshot], start: Date, end: Date)

object RepositoryScenario {
  implicit def RepositoryScenarioArbitrary: Arbitrary[RepositoryScenario] = Arbitrary(for {
      // We pick some date to start the repository at
      date    <- genDate(Date(1900, 1, 1), Date(2100, 12, 31))

      // We will be generating 'span' days with of events (ingestions and/or snapshots).
      span    <- Gen.choose(2, 100)

      // The repository will contain 'n' different namespaces.
      n       <- Gen.choose(3, 10)
      names   <- Gen.listOfN(n, arbitrary[Name])

      // We start with an empty repository and will iterate each day until we end up with a complete view
      init    =  RepositoryScenario(FeatureStore(FeatureStoreId.initial, Nil), Nil, date, date)

      // For each day, take the current feature store, and then:
      //  - calculate a new factset for 'ingestion' using our determined namespaces
      //  - potentiolly generate a snapshot for today (1 out of 2 chance)
      //  - increment the state of the current scenario to track the new snapshot and head of the feature store
      r       <- (1 to span).toList.foldLeftM(init)((acc, _) => for {
        snapshot  <- arbitrary[Boolean]
        today     =  nextDay(acc.end)
        store     =  nextStore(acc.store, names, today)
        snapshots =  nextSnapshots(store, acc.snapshots, today, snapshot)
      } yield RepositoryScenario(store, snapshots, acc.start, today))
    } yield r)

  def nextSnapshots(store: FeatureStore, snapshots: List[Snapshot], today: Date, create: Boolean) =
    snapshots ++ snapshots.map(_.id).maximum.flatMap(_.next).map(id => Snapshot(id, today, store)).filter(_ => create).toList

  def nextStore(store: FeatureStore, names: List[Name], today: Date): FeatureStore = (for {
    id        <- store.id.next
    priority  <- store.factsets.map(_.priority).maximum.flatMap(_.next)
    factsetId <- store.factsets.map(_.value.id).maximum.flatMap(_.next)
    factset   =  Factset(factsetId, names.map(Partition(_, today)))
    nu        =  List(Prioritized(priority, factset))
  } yield FeatureStore(id, store.factsets ++ nu)).getOrElse(store)

  def nextDay(date: Date): Date =
    Date.fromLocalDate(date.localDate.plusDays(1))
}
