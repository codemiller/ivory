package com.ambiata.ivory.mr

import org.apache.hadoop.io.Writable
import org.apache.hadoop.mapreduce.TaskInputOutputContext
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs

/** Abstraction over emitting key/value pairs in an mr job */
trait ContextEmitter[A, B] {
  def emit(key: A, value: B): Unit
}

trait OutputEmitter[A, B] {
  def emitPath(key: A, value: B, path: String): Unit
  def close(): Unit
}

case class MrContextEmitter[K <: Writable, V <: Writable](context: TaskInputOutputContext[_, _, K, V]) extends ContextEmitter[K, V] {
  override def emit(kout: K, vout: V): Unit =
    context.write(kout, vout)
}

case class MrOutputEmitter[K <: Writable, V <: Writable](name: String, writer: MultipleOutputs[K, V]) extends OutputEmitter[K, V] {
  override def close(): Unit =
    writer.close()

  override def emitPath(kout: K, vout: V, path: String): Unit =
    writer.write(name, kout, vout, path)
}
