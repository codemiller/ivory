package com.ambiata.ivory.mr

import org.apache.hadoop.io._

case class TestContextEmitter[K <: Writable, V <: Writable, A](f: (K, V) => A) extends ContextEmitter[K, V] {
  import scala.collection.mutable.ListBuffer
  val emitted: ListBuffer[A] = ListBuffer()
  
  override def emit(kout: K, vout: V): Unit = {
    emitted += f(kout, vout)
    ()
  }
}

case class TestOutputEmitter[K <: Writable, V <: Writable, A](f: (K, V, String) => A) extends OutputEmitter[K, V] {
  import scala.collection.mutable.ListBuffer
  val emitted: ListBuffer[A] = ListBuffer()
  var closed: Boolean = false
  
  override def close(): Unit =
    closed = true

  override def emitPath(kout: K, vout: V, path: String): Unit = {
    emitted += f(kout, vout, path)
    ()
  }
}
