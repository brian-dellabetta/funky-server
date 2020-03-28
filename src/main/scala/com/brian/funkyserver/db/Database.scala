package com.brian.funkyserver.db

import doobie._
import doobie.implicits._
import cats.effect.{IO, ContextShift}

object Database {

  def xa(implicit cs: ContextShift[IO]): Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:postgres",
    "postgres",
    "asdf"
  )
}
