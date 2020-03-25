package com.brian.funkyserver

package object model {

  case class Student(id: Option[Long], firstName: String, lastName: String)

  case object StudentNotFoundError

}
