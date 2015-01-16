package com.ambiata.ivory.mr

import org.apache.hadoop.io._

case class TestEmitter() extends Emitter[BytesWritable, BytesWritable] {
  import scala.collection.mutable.ListBuffer
  val emittedKeys: ListBuffer[String] = ListBuffer()
  val emittedVals: ListBuffer[Array[Byte]] = ListBuffer()
  def emit(kout: BytesWritable, vout: BytesWritable) {
    emittedKeys += new String(kout.copyBytes)
    emittedVals += vout.copyBytes
    ()
  }
}

case class TestMultiEmitter[K <: Writable, V <: Writable, A](f: (K, V, String) => A) extends MultiEmitter[K, V] {
  import scala.collection.mutable.ListBuffer
  val emitted: ListBuffer[(String, A)] = ListBuffer()
  var name: String = null
  var path: String = null
  
  def emit(kout: K, vout: V) {
    emitted += ((name, f(kout, vout, path)))
    ()
  }
}
