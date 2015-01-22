package com.ambiata.ivory.storage.fact

import com.ambiata.ivory.core._

import com.ambiata.poacher.mr._

import org.apache.hadoop.io.{BytesWritable, IntWritable, NullWritable, Writable}

sealed trait MrFactConverter[K <: Writable, V <: Writable] {
  def convert(fact: MutableFact, key: K, value: V, deserialiser: ThriftSerialiser): Unit
}

case class PartitionFactConverter(partition: Partition) extends MrFactConverter[NullWritable, BytesWritable] {
  def convert(fact: MutableFact, key: NullWritable, value: BytesWritable, deserialiser: ThriftSerialiser): Unit = {
    deserialiser.fromBytesViewUnsafe(fact.toThrift, value.getBytes, 0, value.getLength)
    fact.setNspace(partition.namespace.name)
    fact.setYyyyMMdd(partition.date.int)
    ()
  }
}

case class MutableFactConverter() extends MrFactConverter[NullWritable, BytesWritable] {
  def convert(fact: MutableFact, key: NullWritable, value: BytesWritable, deserialiser: ThriftSerialiser): Unit = {
    deserialiser.fromBytesViewUnsafe(fact, value.getBytes, 0, value.getLength)
    ()
  }
}

case class NamespaceDateFactConverter(namespace: Namespace) extends MrFactConverter[IntWritable, BytesWritable] {
  def convert(fact: MutableFact, key: IntWritable, value: BytesWritable, deserialiser: ThriftSerialiser): Unit = {
    deserialiser.fromBytesViewUnsafe(fact.toThrift, value.getBytes, 0, value.getLength)
    fact.setNspace(namespace.name)
    fact.setYyyyMMdd(key.get)
    ()
  }
}
