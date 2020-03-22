package com.brian.funkyserver.repository

import com.brian.funkyserver.model.{Student, StudentNotFoundError}
import doobie.util.transactor.Transactor
import fs2.Stream
import doobie.implicits._
import cats.effect.IO

class Repository(xa: Transactor[IO]) {

  def getStudents: Stream[IO, Student] = {
    sql"SELECT id, first_name, last_name FROM students".query[Student].stream.transact(xa)
  }

  def getStudent(id: Long): IO[Either[StudentNotFoundError.type, Student]] = {
    sql"SELECT id, first_name, last_name FROM students WHERE id=$id".query[Student].option.transact(xa)
      .map {
        case Some(s) => Right(s)
        case _ => Left(StudentNotFoundError)
      }
  }

  def createStudent(s: Student): IO[Student] = {
    sql"INSERT INTO students (first_name, last_name) VALUES (${s.firstName}, ${s.lastName})"
      .update
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)
      .map { id => s.copy(id = id) }
  }
}
