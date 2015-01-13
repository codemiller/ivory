package com.ambiata.ivory.operation.extraction

import com.ambiata.ivory.core._
import com.ambiata.ivory.core.arbitraries.Arbitraries._
import com.ambiata.ivory.core.thrift._
import com.ambiata.poacher.mr._
import org.apache.hadoop.io._
import org.specs2._

class ChordIncrementalMapperSpec extends Specification with ScalaCheck { def is = s2"""

  Counters are consistent across mapper V1  $counters1
  Counters are consistent across mapper V2  $counters2
  Counter totals are correct V1             $totals1
  Counter totals are correct V2             $totals2

"""

  def counters1 = prop((context: ChordMapperSpecContext, priority: Priority) => {
    context.all.foreach(mapV1(_, context, priority))
    (context.ok.counter + context.skip.counter + context.drop.counter) ==== context.all.size
  })

  def counters2 = prop((context: ChordMapperSpecContext, priority: Priority) => {
    context.all.foreach(mapV2(_, context, priority))
    (context.ok.counter + context.skip.counter + context.drop.counter) ==== context.all.size
  })

  def totals1 = prop((context: ChordMapperSpecContext, priority: Priority) => {
    context.all.foreach(mapV1(_, context, priority))
    context.ok.counter ==== context.facts.size and
     context.skip.counter ==== context.skipped.size and
     context.drop.counter ==== context.dropped.size
  })

  def totals2 = prop((context: ChordMapperSpecContext, priority: Priority) => {
    context.all.foreach(mapV2(_, context, priority))
    context.ok.counter ==== context.facts.size and
     context.skip.counter ==== context.skipped.size and
     context.drop.counter ==== context.dropped.size
  })


  def mapV1(f: Fact, context: ChordMapperSpecContext, priority: Priority): Unit = {
    ChordIncrementalMapper.mapV1(
      new NamespacedThriftFact with NamespacedThriftFactDerived
      , new BytesWritable(context.serializer.toBytes(f.toNamespacedThrift))
      , priority
      , Writables.bytesWritable(4096)
      , Writables.bytesWritable(4096)
      , context.emitter
      , context.ok
      , context.skip
      , context.drop
      , context.serializer
      , context.lookup
      , context.entities)
  }

  def mapV2(f: Fact, context: ChordMapperSpecContext, priority: Priority): Unit = {
    ChordIncrementalMapper.mapV2(
      new ThriftFact
      , new IntWritable(f.date.int)
      , new BytesWritable(context.serializer.toBytes(f.toThrift))
      , priority
      , Writables.bytesWritable(4096)
      , Writables.bytesWritable(4096)
      , context.emitter
      , context.ok
      , context.skip
      , context.drop
      , context.serializer
      , context.lookup
      , context.entities
      , f.namespace.name)
  }
}
