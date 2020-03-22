package com.brian.funkyserver

import cats.effect.{IO, ConcurrentEffect, ContextShift, Timer}
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import scala.concurrent.ExecutionContext

import org.http4s.implicits._

import svc.FunkyserverService
import repository.Repository
import db.Database

object FunkyserverServer {

  def stream(implicit T: Timer[IO], C: ContextShift[IO], CE: ConcurrentEffect[IO]): Stream[IO, Nothing] = {
    for {
      client <- BlazeClientBuilder[IO](ExecutionContext.global).stream

      repository = new Repository(Database.xa)

      svc = new FunkyserverService(client, repository)

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.
      httpApp = svc.routes.orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}