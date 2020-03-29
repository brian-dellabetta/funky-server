package com.brian.funkyserver.gql.schema

import cats.effect.Effect
import sangria.schema.{Field, ObjectType, StringType, fields}

object QuerySchema {
  def apply[F[_] : Effect](): ObjectType[Unit, Unit] =
    ObjectType("Query", fields[Unit, Unit](
      Field("hello", StringType, resolve = _ => "Hello World")
    ))
}
