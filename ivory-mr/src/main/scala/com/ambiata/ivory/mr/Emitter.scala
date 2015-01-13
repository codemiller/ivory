package com.ambiata.ivory.mr

import org.apache.hadoop.io.Writable
import org.apache.hadoop.mapreduce.TaskInputOutputContext
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs

/** Abstraction over emitting key/value pairs in an mr job */
trait Emitter[A, B] {
  def emit(key: A, value: B): Unit
}

trait MultiEmitter[A, B] extends Emitter[A, B] {
  var path: String
}

case class MrEmitter[IK <: Writable, IV <: Writable, OK <: Writable, OV <: Writable]() extends Emitter[OK, OV] {
  var context: TaskInputOutputContext[IK, IV, OK, OV] = null

  override def emit(kout: OK, vout: OV): Unit = {
    context.write(kout, vout)
  }
}

case class MrMultiEmitter[K <: Writable, V <: Writable](writer: MultipleOutputs[K, V]) extends MultiEmitter[K, V] {
  var name: String = null
  var path: String = null

  override def emit(kout: K, vout: V): Unit = {
    writer.write(name, kout, vout, path)
  }
}
