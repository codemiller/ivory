package com.ambiata.ivory.mr

import com.ambiata.poacher.mr.ThriftSerialiser
import com.ambiata.ivory.core.thrift._
import org.apache.hadoop.io.BytesWritable

object ThriftByteMutator {

  def from[T](in: BytesWritable, thrift: T, serializer: ThriftSerialiser)(implicit ev: T <:< ThriftLike): Unit = {
    serializer.fromBytesViewUnsafe(thrift, in.getBytes, 0, in.getLength)
    ()
  }

  def mutate[T](in: T, vout: BytesWritable, serializer: ThriftSerialiser)(implicit ev: T <:< ThriftLike): Unit = {
    // It's unfortunate we can't re-use the byte array here too :(
    val bytes = serializer.toBytes(in)
    vout.set(bytes, 0, bytes.length)
  }

  def pipe(in: BytesWritable, vout: BytesWritable): Unit =
    // We are saving a minor step of serialising the (unchanged) thrift fact
    vout.set(in.getBytes, 0, in.getLength)
}
