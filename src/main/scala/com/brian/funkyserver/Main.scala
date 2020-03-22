package com.brian.funkyserver

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object Main extends IOApp {
  def run(args: List[String]) =
    FunkyserverServer.stream.compile.drain.as(ExitCode.Success)
}