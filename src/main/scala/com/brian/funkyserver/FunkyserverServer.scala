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
import gql.GraphQL
import gql.schema.QuerySchema

import doobie.Transactor
import doobie.util.ExecutionContexts
import sangria.schema._
import cats.effect.Effect


object FunkyserverServer {
  // Construct a GraphQL implementation based on our Sangria definitions.
  def graphQL(transactor: Transactor[IO])(implicit blockingContext: ExecutionContext, c: ContextShift[IO]): GraphQL[Unit] =
    new GraphQL[Unit](
      Schema(
        query = QuerySchema[IO]()
        //          mutation = Some(MutationType[IO])
      ),
      //      WorldDeferredResolver[IO],
      //      MasterRepo.fromTransactor(transactor).pure[IO],
      blockingContext
    )

  def stream(implicit T: Timer[IO], C: ContextShift[IO], CE: ConcurrentEffect[IO]): Stream[IO, Nothing] = {
    for {
      client <- BlazeClientBuilder[IO](ExecutionContext.global).stream

      xa = Database.xa

      repository = new Repository(xa)

      gql = graphQL(xa)(ExecutionContext.global, C)

      svc = new FunkyserverService(client, repository, gql)

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