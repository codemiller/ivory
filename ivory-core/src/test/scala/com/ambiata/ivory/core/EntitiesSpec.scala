package com.ambiata.ivory.core

import java.util.HashMap

import org.scalacheck.{Arbitrary, Gen}
import org.specs2.matcher.ThrownExpectations
import org.specs2.{ScalaCheck, Specification}
import Arbitraries._
import scala.collection.JavaConversions._

class EntitiesSpec extends Specification with ScalaCheck with ThrownExpectations { def is = s2"""

 The Entities class provides a list of dates where we wish to get values for each entity

   the keep method keeps a Fact if:
     the fact concerns one of the entities and the fact has a date earlier than the required date $keepFact

   the keepBest method finds facts in a list of facts that have the closest datetime
    to the required date and the best priority $keepBestFact
"""

  def keepFact = prop { (fact: Fact, dateOffset: DateOffset) =>
    val date1 = dateOffset.makeGreaterDateThan(fact.date)
    val entities = add(Entities.empty, fact.entity, date1)
    val diagnostic = Seq(
      s"date1 is $date1",
      s"fact is ${(fact.entity, fact.date, fact.date.int)}",
      s"entities are ${entities.entities.map { case (e, ds) => (e, ds.mkString(",")) }.mkString("\n") }").mkString("\n", "\n", "\n")

    entities.keep(fact) aka diagnostic must beTrue

  }.set(maxSize = 3, minTestsOk= 1000)

  def keepBestFact = prop { (head: (Priority, Fact), tail: List[(Priority, Fact)], dateOffset: DateOffset) =>
    val facts = head +: tail

    // create Entities from the existing facts
    val entities = createEntitiesFromFactsWithOneMoreDate(facts, dateOffset)
    val (priority1, fact1) = head
    val (entity1, date1)   = (fact1.entity, fact1.date.int)

    val best = entities.keepBestFacts(entity1, facts).toList

    "there are 'best' facts for entity1, given how we've built the Entities object" ==> {
      best must not(beEmpty)
    }

    "there is a fact for date1" ==> {
      val diagnostic =
        s"\n\nentity1: $entity1, date1: ${date1}, priority1: $priority1\n\n" +
        facts.collect { case (p, f) => (p, f.entity, f.date.int) }.mkString("FACTS are\n", "\n", "\n\n") +
        best.collect { case (d, p, Some(f)) => (d, p, f.entity, f.date.int) }.mkString("BEST is\n", "\n", "\n\n")

      best.find(_._1 == date1).flatMap(_._3) aka diagnostic must beSome(fact1)
    }
  }.set(maxSize = 3, minTestsOk= 1000)

  /**
   * ARBITRARIES
   */

  def createEntitiesFromFactsWithOneMoreDate(facts: List[(Priority, Fact)], dateOffset: DateOffset) = {
    facts.foldLeft(Entities(new HashMap[String, Array[Int]])) { case (entities, (p, f)) =>
      add(entities, f.entity, f.date)
    }
  }

  /** add a new entity name and date to the entities list */
  def add(entities: Entities, entity: String, date: Date) = {
    val dates =
      Option(entities.entities.get(entity))
        .map(ds => (ds :+ date.int).toArray).getOrElse(Array(date.int)).sorted.reverse

    entities.entities.put(entity, dates)
    entities
  }

  def genEntityDates: Gen[(Entity, List[Date])] = for {
    id          <- EntityArbitrary.arbitrary
    datesNumber <- Gen.choose(1, 4)
    dates       <- Gen.listOfN(datesNumber, DateArbitrary.arbitrary)
  } yield (id, dates)

  def genEntities: Gen[Entities] = Gen.sized { n =>
    Gen.listOfN(n + 1, genEntityDates).map { list =>
      val mappings = new java.util.HashMap[String, Array[Int]]
      list.foreach { case (entity, dates) => mappings.put(entity.value, dates.map(_.int).toArray) }
      Entities(mappings)
    }
  }

  implicit def EntitiesArbitrary: Arbitrary[Entities] =
    Arbitrary(genEntities)
}
