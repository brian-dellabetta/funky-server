package com.brian.funkyserver.svc

import cats.effect.Sync
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.implicits._
import org.http4s.client.Client

class FunkyserverService[F[_] : Sync](client: Client[F]) extends Http4sDsl[F] {

  def routes(): HttpRoutes[F] = {

    val h = HelloWorld.impl[F]
    val j = Jokes.impl[F](client)

    HttpRoutes.of[F] {
      case GET -> Root / "joke" =>
        for {
          joke <- j.get
          resp <- Ok(joke)
        } yield resp


      case GET -> Root / "hello" / name =>
        for {
          greeting <- h.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
    }
  }
}
