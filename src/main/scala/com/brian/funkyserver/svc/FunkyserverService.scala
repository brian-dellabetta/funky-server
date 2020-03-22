package com.brian.funkyserver.svc

import cats.effect.Sync
import org.http4s.{HttpRoutes, MediaType, Uri}
import org.http4s.headers.{Location, `Content-Type`}
import org.http4s.dsl.Http4sDsl
import cats.implicits._
import org.http4s.client.Client
import fs2.Stream
import io.circe.syntax._
import io.circe.generic.auto._

import com.brian.funkyserver.model.{StudentNotFoundError, Student}

import com.brian.funkyserver.repository.Repository


import io.circe.{Encoder, Json}
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf
import cats.effect.IO

class FunkyserverService(client: Client[IO], repository: Repository) extends Http4sDsl[IO] {

  def routes(): HttpRoutes[IO] = {

    val h = HelloWorld.impl[IO]
    val j = Jokes.impl[IO](client)

    HttpRoutes.of[IO] {
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

      //Get students
      //      case GET -> Root / "students" =>
      //        Ok(
      //          repository.getStudents.map(_.asJson.noSpaces).intersperse(","),
      //          `Content-Type`(MediaType.application.json)
      //        )

      //Get student by id
      case GET -> Root / "students" / LongVar(id) =>
        for {
          result <- repository.getStudent(id)
          resp <- studentResult(result)
        } yield resp
    }
  }

  //  implicit val studentEncoder: Encoder[Student] = new Encoder[Student] {
  //    final def apply(s: Student): Json = Json.obj(
  //      ("id", Json.fromLong(s.id)),
  //      ("firstName", Json.fromString(s.firstName)),
  //      ("lastName", Json.fromString(s.lastName)),
  //    )
  //  }
  //
  //  implicit def studentEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Student] =
  //    jsonEncoderOf[F, Student]

  //https://github.com/http4s/http4s/issues/1648
  implicit def jsonEncoder[A <: Product : Encoder, F[_] : Sync]: EntityEncoder[F, A] =
    jsonEncoderOf[F, A]

  private def studentResult(result: Either[StudentNotFoundError.type, Student]) = {
    result match {
      case Left(_) => NotFound()
      case Right(student) => Ok(student.asJson)
    }
  }
}
