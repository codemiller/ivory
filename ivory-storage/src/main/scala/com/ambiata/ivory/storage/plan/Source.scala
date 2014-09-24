package com.ambiata.ivory.storage.plan

import com.ambiata.mundane.control._
import scalaz._, Scalaz._, effect.IO

object Source {
  def apply[A, B](f: A => B): Kleisli[Id, A, B] =
    Kleisli[Id, A, B](f.map(_.point[Id]))

  def effect[A, B](f: A => ResultT[IO, B]): Kleisli[ResultTIO, A, B] =
    Kleisli[ResultTIO, A, B](f)
}
