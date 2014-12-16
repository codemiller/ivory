package com.ambiata.ivory.mr

import org.apache.hadoop.mapreduce.Mapper
import org.apache.hadoop.mapreduce.InputSplit

abstract class CombinableMapper[A, B, C, D] extends Mapper[A, B, C, D] {
  /* Normal MR semantics, once per mapper, except that context.getInputSplit isn't that useful because it may be a ProxyInputSplit. */
  override def setup(context: Mapper[A, B, C, D]#Context): Unit = {}
  /* A special setup call that happens once each time input split changes (including first record). */
  def setupSplit(context: Mapper[A, B, C, D]#Context, split: InputSplit): Unit = {}
  /* Normal MR semantics. */
  override def cleanup(context: Mapper[A, B, C, D]#Context): Unit = {}
  /* Normal MR semantics. */
  override def map(k: A, v: B, context: Mapper[A, B, C, D]#Context): Unit = {}
  /* Explicitly disabled. */
  override final def run(context: Mapper[A, B, C, D]#Context): Unit = { sys.error("INVARIANT VIOLATED: Run for CombinableMapper should be managed by ProxyMapper.") }
}
