package com.ambiata.ivory.storage.fact

import com.ambiata.ivory.core._
import com.ambiata.ivory.lookup.FactsetLookup

import com.ambiata.mundane.io.FilePath

import com.ambiata.poacher.mr._

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.mapreduce.InputSplit

import scalaz._, Scalaz._

case class FactsetInfo(factsetId: FactsetId, partition: Partition, priority: Priority)

object FactsetInfo {
  def getBaseInfo(inputSplit: InputSplit): (FactsetId, Partition) = {
    val path = FilePath.unsafe(MrContext.getSplitPath(inputSplit).toString)
    Factset.parseFile(path) match {
      case Success(r) => r
      case Failure(e) => Crash.error(Crash.DataIntegrity, s"Can not parse factset path ${e}")
    }
  }


  def fromMr(thriftCache: ThriftCache, factsetLookupKey: ThriftCache.Key,
             configuration: Configuration, inputSplit: InputSplit): FactsetInfo = {
    val (factsetId, partition) = getBaseInfo(inputSplit)

    val priorityLookup = new FactsetLookup <| (fl => thriftCache.pop(configuration, factsetLookupKey, fl))
    val priority = priorityLookup.priorities.get(factsetId.render)
    FactsetInfo(factsetId, partition, Priority.unsafe(priority))
  }
}
