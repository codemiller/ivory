package com.ambiata.ivory.operation.extraction.output

import com.ambiata.ivory.core.arbitraries.ArbitraryValues
import com.ambiata.mundane.control._
import com.ambiata.mundane.io._
import com.ambiata.mundane.testing.ResultTIOMatcher._
import com.ambiata.ivory.core._
import com.ambiata.ivory.core.arbitraries._
import ArbitraryFacts._
import com.ambiata.ivory.storage.legacy._
import com.ambiata.ivory.storage.repository._
import com.ambiata.ivory.operation.extraction.Snapshots
import org.specs2.matcher.ThrownExpectations
import org.specs2._

class DenseOutputSpec extends Specification with SampleFacts with ThrownExpectations with ScalaCheck { def is = s2"""

 A Sequence file containing feature values can be
   pivoted as a row-oriented file, example 1           $dense       ${tag("mr")}
   pivoted as a row-oriented file, example 2           $dense2      ${tag("mr")}

 A dense file must must the dictionary output          $matchDict   ${tag("mr")}

"""
  def dense =
    RepositoryBuilder.using(createDense(sampleFacts, sampleDictionary)) must beOkValue(
      """|eid1|abc|NA|NA
         |eid2|NA|11|NA
         |eid3|NA|NA|true
      """.stripMargin.trim -> expectedDictionary
    )

  def dense2 = {
    val facts = List(
      IntFact(      "eid1", FeatureId(Name("ns1"), "fid2"), Date(2012, 10,  1), Time(0), 10)
      , IntFact(    "eid3", FeatureId(Name("ns1"), "fid2"), Date(2012, 10,  1), Time(0), 10)
      , StringFact( "eid1", FeatureId(Name("ns1"), "fid1"), Date(2012,  9,  1), Time(0), "abc")
      , StringFact( "eid1", FeatureId(Name("ns1"), "fid1"), Date(2012, 10,  1), Time(0), "ghi")
      , StringFact( "eid1", FeatureId(Name("ns1"), "fid1"), Date(2012,  7,  2), Time(0), "def")
      , IntFact(    "eid2", FeatureId(Name("ns1"), "fid2"), Date(2012, 10,  1), Time(0), 10)
      , IntFact(    "eid2", FeatureId(Name("ns1"), "fid2"), Date(2012, 11,  1), Time(0), 11)
      , BooleanFact("eid3", FeatureId(Name("ns2"), "fid3"), Date(2012,  3, 20), Time(0), true)
    )
    RepositoryBuilder.using(createDense(List(facts), sampleDictionary)) must beOkValue(
      """|eid1|ghi|10|NA
         |eid2|NA|11|NA
         |eid3|NA|10|true
      """.stripMargin.trim -> expectedDictionary
    )
  }

  def matchDict = prop {(facts: FactsWithDictionary) =>
    RepositoryBuilder.using(createDense(List(facts.facts), facts.dictionary)) must beOkLike {
      case (out, dict) => seqToResult(out.lines.toList.map(_.split("\\|", -1).size - 1 ==== dict.size))
    }
  }.set(minTestsOk = 1)

  def expectedDictionary = List(
    "0|ns1|fid1|string|categorical|desc|NA",
    "1|ns1|fid2|int|numerical|desc|NA",
    "2|ns2|fid3|boolean|categorical|desc|NA"
  )

  def createDense(facts: List[List[Fact]], dictionary: Dictionary)(repo: HdfsRepository): ResultTIO[(String, List[String])] =
    TemporaryDirPath.withDirPath { dir =>
      for {
        _     <- RepositoryBuilder.createRepo(repo, dictionary, facts)
        dense <- IvoryLocation.fromUri((dir </> "dense").path, IvoryConfiguration.Empty)
        res   <- Snapshots.takeSnapshot(repo, Date.maxValue)

        meta      = res.meta
        input     = repo.toIvoryLocation(Repository.snapshot(meta.snapshotId))
        _                <- DenseOutput.createWithDictionary(repo, input, dense, dictionary, '|', "NA")
        dictLocation     <- IvoryLocation.fromUri((dir </> "dense" </> ".dictionary").path, IvoryConfiguration.Empty)
        dictionaryLines  <- IvoryLocation.readLines(dictLocation)
        denseLines       <- IvoryLocation.readLines(dense)
      } yield (denseLines.mkString("\n").trim, dictionaryLines)
    }
}