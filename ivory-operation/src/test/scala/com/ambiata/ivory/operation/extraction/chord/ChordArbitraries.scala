package com.ambiata.ivory.operation.extraction.chord

import com.ambiata.ivory.core.Arbitraries._
import com.ambiata.ivory.core._
import com.ambiata.ivory.operation.extraction.snapshot.SnapshotWindows
import org.scalacheck._

object ChordArbitraries {

  def incValue(f: Fact, post: String): Fact =
    f.withValue(StringValue(Value.toStringWithStruct(f.value, "") + post))

  /** Represents a single entity with a number of dates, and for each date a number of possible facts */
  case class ChordEntity(entity: String, dates: List[(Date, List[Date])], above: List[Date]) {
    lazy val dateArray: Array[Int] =
      dates.map(_._1.int).sorted.reverse.toArray

    def fromFact(fact: Fact): List[(Date, List[Fact])] =
      dates.sortBy(_._1).map({ case (date, dates) => date ->
        dates.sorted.map(fact.withDate).zipWithIndex.map({
          case (fact, index) =>
            fact.withValue(StringValue(List(entity, date.hyphenated, index).mkString(" ")))
        })
      })

    /** Creates facts but ensures each have a unique value to confirm priority actually works */
    def fromFactWithPriority(fact: Fact): List[(Date, List[Fact])] =
      fromFact(fact).map({ case (date, facts) =>
        date -> facts.zip(facts.map(incValue(_, "p"))).flatMap(f => List(f._1, f._2)) })

    def toFacts[A](fact: Fact)(t: (Date, List[Fact], List[Fact]) => List[A]): List[A] =
      fromFact(fact).foldLeft(List[Fact]() -> List[A]())({ case ((prev, as), (date, fact)) =>
        (prev ++ fact) -> (as ++ t(date, fact, prev))
      })._2

    def toFactsWithPriority[A](fact: Fact)(t: (Date, List[Fact], List[Fact]) => List[A]): List[A] =
      fromFactWithPriority(fact).foldLeft(List[Fact]() -> List[A]())({ case ((prev, as), (date, fact)) =>
        (prev ++ fact) -> (as ++ t(date, fact, prev))
      })._2

    def facts(fact: Fact): List[Fact] =
      toFacts(fact)((_, fs, _) => fs).map(_.withEntity(entity))

    // Duplicate every fact with priority version but different value
    def factsWithPriority(fact: Fact): List[Fact] =
      toFactsWithPriority(fact)((_, fs, _) => fs).map(_.withEntity(entity))

    def expected(fact: Fact): List[Fact] =
      // Take the latest fact, which may come from a previous chord period if this one is empty
      toFacts(fact)(expecteds)

    def expectedSet(fact: Fact): List[Fact] =
      // Take the latest fact, which may come from a previous chord period if this one is empty
      toFactsWithPriority(fact)(expecteds)

    def expecteds: (Date, List[Fact], List[Fact]) => List[Fact] =
      (d, fs, prev) => (prev ++ fs).lastOption.map(_.withEntity(entity + ":" + d.hyphenated)).toList

    def expectedWindow(fact: Fact, window: Option[Window]): List[Fact] =
      toFacts(fact)(expectedWindows(window)).distinct

    def expectedWindowSet(fact: Fact, window: Option[Window]): List[Fact] =
      toFactsWithPriority(fact)(expectedWindows(window)).distinct

    def expectedWindows: Option[Window] => (Date, List[Fact], List[Fact]) => List[Fact] =
      window => (d, fs, prev) =>
        window.map(SnapshotWindows.startingDate(_, d)).map {
          sd =>
            // _Always_ emit the last fact before the window (for state-based features)
            (prev ++ fs).filter(_.date.int < sd.int).lastOption.toList ++
              // All the facts from the window
              fs.filter(_.date.int >= sd.int)
        }.getOrElse((prev ++ fs).lastOption.toList).map(_.withEntity(entity))
  }

  /** A single entity, for testing [[com.ambiata.ivory.operation.extraction.ChordReducer]] */
  case class ChordFact(ce: ChordEntity, fact: Fact, window: Option[Window]) {
    lazy val facts: List[Fact] = ce.facts(fact)
    lazy val factsWithPriority: List[Fact] = ce.factsWithPriority(fact)
    lazy val expected: List[Fact] = ce.expected(fact)
    lazy val expectedSet: List[Fact] = ce.expectedSet(fact)
    lazy val expectedWindow: List[Fact] = ce.expectedWindow(fact, window)
    lazy val expectedWindowSet: List[Fact] = ce.expectedWindowSet(fact, window)
    lazy val windowDateArray: Option[Array[Int]] = window.map {
      win => ce.dates.map(_._1).map(SnapshotWindows.startingDate(win, _).int).sorted.reverse.toArray
    }
  }

  case class ChordFacts(ces: List[ChordEntity], fid: FeatureId, factAndMeta: SparseEntities, other: List[Fact]) {
    lazy val facts: List[Fact] = (ces.flatMap(_.facts(factAndMeta.fact)) ++ above ++ other).map(_.withFeatureId(fid))
    lazy val above: List[Fact] = ces.flatMap(_.above.map(factAndMeta.fact.withDate))
    lazy val expected: List[Fact] = ces.flatMap(_.expected(factAndMeta.fact)).map(_.withFeatureId(fid))
    lazy val dictionary: Dictionary = Dictionary(List(factAndMeta.meta.toDefinition(fid)))
    // If the oldest chord has no facts then don't capture a snapshot (it will error at the moment)
    // https://github.com/ambiata/ivory/issues/343
    lazy val takeSnapshot: Boolean = ces.sortBy(_.dates.headOption.map(_._1).getOrElse(Date.maxValue)).headOption
      .flatMap(_.dates.headOption).flatMap(_._2.headOption).isDefined
  }

  implicit def ChordFactArbitrary: Arbitrary[ChordFact] = Arbitrary(for {
    e     <- chordEntityGen(0)
    // Just generate one stub fact - we only care about the entity and date
    fact  <- Arbitrary.arbitrary[Fact]
    win   <- Arbitrary.arbitrary[Option[Window]]
  } yield ChordFact(e, fact, win))

  implicit def ChordFactsArbitrary: Arbitrary[ChordFacts] = Arbitrary(for {
    n     <- Gen.choose(1, 20)
    dates <- Gen.sequence[List, ChordEntity]((0 until n).map(chordEntityGen))
    // Just generate one stub fact - we only care about the entity and date
    fact  <- Arbitrary.arbitrary[SparseEntities]
    fid   <- Arbitrary.arbitrary[FeatureId]
    // Generate other facts that aren't related in any way - they should be ignored
    o     <- Gen.choose(1, 3).flatMap(i => Gen.listOfN(i, Arbitrary.arbitrary[Fact]))
  } yield ChordFacts(dates, fid, fact, o))

  /** Use an increasing number to represent the entity to avoid name clashes */
  def chordEntityGen(e: Int): Gen[ChordEntity] = for {
    m     <- Gen.choose(2, 5)
    dates <- Gen.listOfN(m, Arbitrary.arbitrary[Date]).flatMap {
      ds => Gen.sequence[List, (Date, List[Date])](ds.sorted.sliding(2).filter(_.length == 2).zipWithIndex.map {
        case (List(d1, d2), i) => for {
          // NOTE: We're possibly generating an empty set of fact dates for _all_ chord dates,
          // which is technically impossible but is gracefully handled in ChordJob.reduce to make this simpler
          i  <- Gen.choose(0, 3)
          df <- Gen.listOfN(i, genDate(Date.fromLocalDate(d1.localDate.plusDays(1)), d2))
        } yield d2 -> df.distinct
      }.toList)
    }
    // Generate a few dates above the highest chord date, only needed by ChordSpec but easier to do here
    last   = dates.map(_._1).last
    above <- Gen.choose(1, 3).flatMap(i => Gen.listOfN(i, genDate(Date.fromLocalDate(last.localDate.plusDays(1)), Date.maxValue)))
  } yield ChordEntity(e.toString, dates, above)
}