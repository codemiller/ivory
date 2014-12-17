package com.ambiata.ivory.operation.extraction.reduction

import com.ambiata.ivory.core.arbitraries.Arbitraries._
import com.ambiata.ivory.core._
import com.ambiata.ivory.core.IntFact
import org.specs2.{ScalaCheck, Specification}
import com.ambiata.ivory.core.thrift.ThriftFactValue


class InverseReducerSpec extends Specification with ScalaCheck { def is = s2"""
  Inverse count reducer works with arbitrary facts       $countInverse
  Inverse mean reducer works with arbitrary int facts    $inverseMeanInt

"""

  def countInverse = prop((facts: List[Fact]) => {
    val r = new InverseReducer(new CountReducer())
    facts.foreach(r.update)
    r.save ==== ThriftFactValue.d(if (facts.count(!_.isTombstone) == 0) Double.NaN else 1.0 / facts.count(!_.isTombstone))
  })

  def inverseMeanInt = prop((xs: List[Int]) => {
    val r = new InverseReducer(new ReductionFoldWrapper(new MeanReducer[Int], ReductionValueInt, ReductionValueDouble))
    xs.foreach{ case i => 
      val newFact = Fact.newFact("", "", "", Date.minValue, Time(3), IntValue(i))
      r.update(newFact)
    }
    r.save ==== ThriftFactValue.d(if (xs.length == 0 || xs.foldLeft(0)(_+_).toDouble / xs.length == 0) Double.NaN else 1.0 / (xs.foldLeft(0)(_+_).toDouble / xs.length))
  })
}
