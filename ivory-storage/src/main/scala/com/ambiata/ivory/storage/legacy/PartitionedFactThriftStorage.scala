package com.ambiata.ivory.storage.legacy

import com.ambiata.ivory.storage.fact.{FactsetVersionTwo, FactsetVersion, FactsetVersionOne}

import scalaz.{Name => _, DList => _, Value => _, _}
import com.nicta.scoobi.Scoobi._
import org.apache.hadoop.io.compress.CompressionCodec
import org.apache.hadoop.fs.Path
import com.ambiata.mundane.io._

import com.ambiata.ivory.core._
import com.ambiata.ivory.core.thrift._
import com.ambiata.poacher.scoobi._
import com.ambiata.ivory.scoobi._
import FactFormats._

import java.net.URI

trait PartitionFactThriftStorage {
  val parsePartition: String => ParseError \/ Partition = scalaz.Memo.mutableHashMapMemo { path: String =>
    Partition.parseFile(FilePath(path)).leftMap(ParseError.withLine(path)).disjunction
  }

  def parseFact(path: String, tfact: ThriftFact): ParseError \/ Fact =
    parsePartition(path).map(p => createFact(p, tfact))

  def createFact(partition: Partition, tfact: ThriftFact): Fact =
    FatThriftFact(partition.namespace.name, partition.date, tfact)

  def loadScoobiFromPaths(paths: List[FilePath]): ScoobiAction[DList[ParseError \/ Fact]] =
    ScoobiAction.scoobiJob({ implicit sc: ScoobiConfiguration =>
      if(paths.nonEmpty)
        valueFromSequenceFileWithPaths[ThriftFact](paths.map(_.path).toSeq).map({ case (path, tfact) => parseFact(path, tfact) })
      else
        DList[ParseError \/ Fact]()
    })

  val partitionPath: ((String, Date)) => String = scalaz.Memo.mutableHashMapMemo { nsd =>
    Partition.stringPath(nsd._1, nsd._2)
  }

  case class PartitionedFactThriftStorer(base: String, codec: Option[CompressionCodec]) extends IvoryScoobiStorer[Fact, DList[(PartitionKey, ThriftFact)]] {
    def storeScoobi(dlist: DList[Fact])(implicit sc: ScoobiConfiguration): DList[(PartitionKey, ThriftFact)] = {
      dlist.by(f => partitionPath((f.namespace.name, f.date)))
           .mapValues((f: Fact) => f.toThrift)
           .valueToPartitionedSequenceFile[PartitionKey, ThriftFact](base, identity, overwrite = true).persistWithCodec(codec)
    }
  }
}

object PartitionFactThriftStorageV1 extends PartitionFactThriftStorage
object PartitionFactThriftStorageV2 extends PartitionFactThriftStorage

object PartitionFactThriftStorage {
  def parseThriftFact(factsetVersion: FactsetVersion, path: String)(tfact: ThriftFact): ParseError \/ Fact =
    factsetVersion match {
      case FactsetVersionOne => PartitionFactThriftStorageV1.parseFact(path, tfact)
      case FactsetVersionTwo => PartitionFactThriftStorageV2.parseFact(path, tfact)
    }
}
