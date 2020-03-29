package com.brian.funkyserver.svc

import cats.effect.Sync
import org.http4s.{HttpRoutes, MediaType, Uri}
import org.http4s.headers.{Location, `Content-Type`}
import org.http4s.dsl.Http4sDsl
import cats.implicits._
import org.http4s.client.Client
import fs2.{Stream, Chunk}
import io.circe.syntax._
import io.circe.generic.auto._

import com.brian.funkyserver.model.{StudentNotFoundError, Student}

import com.brian.funkyserver.repository.Repository
import com.brian.funkyserver.gql.GraphQL

import io.circe.{Decoder, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import cats.effect.IO
import cats.Applicative


class FunkyserverService(client: Client[IO], repository: Repository, graphql: GraphQL[Unit]) extends Http4sDsl[IO] {

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

      //GraphQL
      case req@POST -> Root / "graphql" =>
        req.as[Json].flatMap(graphql.query).flatMap {
          case Right(json) => Ok(json.noSpaces)
          case Left(json) => BadRequest(json.noSpaces)
        }

      //Get students
      case GET -> Root / "students" =>
        Ok(
          repository.getStudents.map(_.asJson.noSpaces).intersperse(",").cons(Chunk("[")) ++ Stream.chunk(Chunk("]")),
          `Content-Type`(MediaType.application.json)
        )

      //Get student by id
      case GET -> Root / "students" / LongVar(id) =>
        for {
          result <- repository.getStudent(id)
          resp <- result match {
            case Left(_) => NotFound()
            case Right(student) => Ok(student.asJson.noSpaces)
          }
        } yield resp

      //Create student
      case req@POST -> Root / "students" =>
        for {
          //student <- req.decode[Student]
          student <- req.as[Student]
          createdStudent <- repository.createStudent(student)
          resp <- Created(createdStudent.asJson.noSpaces, Location(Uri.unsafeFromString(s"/students/${createdStudent.id}")))
        } yield resp

      //Update student
      case req@PUT -> Root / "students" / LongVar(id) =>
        for {
          student <- req.as[Student]
          result <- repository.updateStudent(id, student)
          resp <- result match {
            case Left(_) => NotFound()
            case Right(student) => Ok(student.asJson.noSpaces)
          }
        } yield resp

      //Delete student
      case DELETE -> Root / "students" / LongVar(id) =>
        repository.deleteStudent(id).flatMap {
          case Left(_) => NotFound()
          case Right(_) => NoContent()
        }
    }
  }

  //Encoder[Student] / Decoder[Student] provided by io.circe.generic.auto._
  //  implicit val studentEncoder: Encoder[Student] = new Encoder[Student] {
  //    final def apply(s: Student): Json = Json.obj(
  //      ("id", Json.fromLong(s.id)),
  //      ("firstName", Json.fromString(s.firstName)),
  //      ("lastName", Json.fromString(s.lastName)),
  //    )
  //  }
  //  implicit val studentDecoder: Decoder[Student] = Decoder.forProduct3("id", "firstName", "lastName")(Student.apply)

  //https://github.com/http4s/http4s/issues/1648
  //  implicit def jsonEncoder[A <: Product : Encoder, F[_] : Sync]: EntityEncoder[F, A] =
  //    jsonEncoderOf[F, A]

  implicit def entityIODecoder[A <: Product : Decoder]: EntityDecoder[IO, A] = jsonOf[IO, A]
}
