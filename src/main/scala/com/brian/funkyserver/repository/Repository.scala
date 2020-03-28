package com.brian.funkyserver.repository

import com.brian.funkyserver.model.{Student, StudentNotFoundError}
import doobie.util.transactor.Transactor
import fs2.Stream
import doobie.implicits._
import cats.effect.IO

class Repository(xa: Transactor[IO]) {

  def getStudents: Stream[IO, Student] = {
    sql"SELECT id, first_name, last_name FROM student".query[Student].stream.transact(xa)
  }

  def getStudent(id: Long): IO[Either[StudentNotFoundError.type, Student]] = {
    sql"SELECT id, first_name, last_name FROM student WHERE id=$id".query[Student].option.transact(xa)
      .map {
        case Some(s) => Right(s)
        case _ => Left(StudentNotFoundError)
      }
  }

  def createStudent(s: Student): IO[Student] = {
    sql"INSERT INTO student (first_name, last_name) VALUES (${s.firstName}, ${s.lastName})"
      .update
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)
      .map { id => s.copy(id = Some(id)) }
  }

  def updateStudent(id: Long, s: Student): IO[Either[StudentNotFoundError.type, Student]] = {
    sql"UPDATE student set first_name = ${s.firstName}, last_name = ${s.lastName} where id = ${id}"
      .update
      .run
      .transact(xa)
      .map { affectedRows =>
        if (affectedRows == 1) Right(s.copy(id=Some(id))) else Left(StudentNotFoundError)
      }
  }

  def deleteStudent(id: Long): IO[Either[StudentNotFoundError.type, Unit]] = {
    sql"DELETE FROM student where id = ${id}"
      .update
      .run
      .transact(xa)
      .map { affectedRows =>
        if (affectedRows == 1) Right(()) else Left(StudentNotFoundError)
      }
  }
}
