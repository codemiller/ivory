package com.ambiata.ivory.core

import org.specs2._
import scalaz._

class ListsSpec extends Specification with ScalaCheck { def is = s2"""

Lists Tests
-----------

  findMapM:
    finding the first element performs transform and only runs only necessary effects   $first
    finding the last element performs transform and runs all effects (once only)        $last
    runs all effects but doesn't return a value for not found                           $notFound

"""
  import Lists._

  type StateInt[A] = State[Int, A]

  def first = prop((x: Int, xs: List[Int]) =>
    findMapM[StateInt, Int, Int](x :: xs)(z =>
      State[Int, Option[Int]](n => (n + 1, Some(z * 2)))
    ).run(0) must_== (1 -> Some(x * 2)))

  def last = prop((x: Int, xs: List[Int]) => !xs.contains(x) ==> {
    findMapM[StateInt, Int, Int](xs ++ List(x))(z =>
      if (z == x)
        State[Int, Option[Int]](n => (n + 1, Some(x * 2)))
      else
        State[Int, Option[Int]](n => (n + 1, None))
    ).run(0) must_== ((xs.length + 1) -> Some(x * 2)) })

  def notFound = prop((xs: List[Int]) =>
    findMapM[StateInt, Int, Int](xs)(z =>
      State[Int, Option[Int]](n => (n + 1, None))
    ).run(0) must_== (xs.length -> None))
}
