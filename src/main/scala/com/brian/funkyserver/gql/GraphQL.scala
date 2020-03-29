package com.brian.funkyserver.gql

import io.circe.{Json, JsonObject}

import scala.concurrent.ExecutionContext
import cats._
import cats.effect._
import cats.implicits._

import io.circe.{Json, JsonObject}
import io.circe.optics.JsonPath.root

import sangria.execution.WithViolations
import sangria.parser.{QueryParser, SyntaxError}

import sangria.ast._
import sangria.execution._
import sangria.execution.deferred._
import sangria.marshalling.circe._
import sangria.schema._
import sangria.validation._

import scala.util.{Success, Failure}

//trait GraphQL[F[_]] {
//
//  /**
//    * Executes a JSON-encoded request in the standard POST encoding, described thus in the spec:
//    *
//    * A standard GraphQL POST request should use the application/json content type, and include a
//    * JSON-encoded body of the following form:
//    *
//    * {
//    * "query": "...",
//    * "operationName": "...",
//    * "variables": { "myVariable": "someValue", ... }
//    * }
//    *
//    * `operationName` and `variables` are optional fields. `operationName` is only required if
//    * multiple operations are present in the query.
//    *
//    * @return either an error Json or result Json
//    */
//  def query(request: Json): F[Either[Json, Json]]
//
//  /**
//    * Executes a request given a `query`, optional `operationName`, and `varianbles`.
//    *
//    * @return either an error Json or result Json
//    */
//  def query(query: String, operationName: Option[String], variables: JsonObject): F[Either[Json, Json]]
//
//}

// Construct a GraphQL implementation based on our Sangria definitions.
class GraphQL[A](
                  schema: Schema[A, Unit],
                  deferredResolver: DeferredResolver[A],
                  userContext: IO[A],
                  blockingExecutionContext: ExecutionContext
                )(
                  implicit F: MonadError[IO, Throwable],
                  L: LiftIO[IO],
                  C: ContextShift[IO]
                ) {

  // Some circe lenses
  private val queryStringLens = root.query.string
  private val operationNameLens = root.operationName.string
  private val variablesLens = root.variables.obj

  // Format a SyntaxError as a GraphQL `errors`
  private def formatSyntaxError(e: SyntaxError): Json = Json.obj(
    "errors" -> Json.arr(Json.obj(
      "message" -> Json.fromString(e.getMessage),
      "locations" -> Json.arr(Json.obj(
        "line" -> Json.fromInt(e.originalError.position.line),
        "column" -> Json.fromInt(e.originalError.position.column))))))

  // Format a WithViolations as a GraphQL `errors`
  private def formatWithViolations(e: WithViolations): Json = Json.obj(
    "errors" -> Json.fromValues(e.violations.map {
      case v: AstNodeViolation => Json.obj(
        "message" -> Json.fromString(v.errorMessage),
        "locations" -> Json.fromValues(v.locations.map(loc => Json.obj(
          "line" -> Json.fromInt(loc.line),
          "column" -> Json.fromInt(loc.column)))))
      case v => Json.obj(
        "message" -> Json.fromString(v.errorMessage))
    }))

  // Format a String as a GraphQL `errors`
  private def formatString(s: String): Json = Json.obj(
    "errors" -> Json.arr(Json.obj(
      "message" -> Json.fromString(s))))

  // Format a Throwable as a GraphQL `errors`
  private def formatThrowable(e: Throwable): Json = Json.obj(
    "errors" -> Json.arr(Json.obj(
      "class" -> Json.fromString(e.getClass.getName),
      "message" -> Json.fromString(e.getMessage))))


  // Destructure `request` and delegate to the other overload.
  def query(request: Json): IO[Either[Json, Json]] = {
    val queryString = queryStringLens.getOption(request)
    val operationName = operationNameLens.getOption(request)
    val variables = variablesLens.getOption(request).getOrElse(JsonObject())
    queryString match {
      case Some(qs) => query(qs, operationName, variables)
      case None => F.pure(formatString("No 'query' property was present in the request.").asLeft)
    }
  }

  // Parse `query` and execute.
  def query(query: String, operationName: Option[String], variables: JsonObject): IO[Either[Json, Json]] =
    QueryParser.parse(query) match {
      case Success(ast) => exec(schema, userContext, ast, operationName, variables)(blockingExecutionContext)
      case Failure(e@SyntaxError(_, _, pe)) => fail(formatSyntaxError(e))
      case Failure(e) => fail(formatThrowable(e))
    }

  // Lift a `Json` into the error side of our effect.
  def fail(j: Json): IO[Either[Json, Json]] =
    F.pure(j.asLeft)

  def exec(
            schema: Schema[A, Unit],
            userContext: IO[A],
            query: Document,
            operationName: Option[String],
            variables: JsonObject
          )(implicit ec: ExecutionContext): IO[Either[Json, Json]] =
    userContext.flatMap { ctx =>
      IO.fromFuture {
        IO {
          Executor.execute(
            schema = schema,
            deferredResolver = deferredResolver,
            queryAst = query,
            userContext = ctx,
            variables = Json.fromJsonObject(variables),
            operationName = operationName,
            exceptionHandler = ExceptionHandler {
              case (_, e) => HandledException(e.getMessage)
            }
          )
        }
      }
    }.attempt.flatMap {
      case Right(json) => F.pure(json.asRight)
      case Left(err: WithViolations) => fail(formatWithViolations(err))
      case Left(err: Throwable) => F.pure(formatThrowable(err).asLeft)
    }
}

