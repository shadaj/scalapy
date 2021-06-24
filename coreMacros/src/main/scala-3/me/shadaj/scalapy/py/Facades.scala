package me.shadaj.scalapy.py

import scala.reflect.macros.whitebox
import scala.language.experimental.macros

import scala.annotation.StaticAnnotation
class native extends StaticAnnotation

object FacadeImpl {
  def creator = ???

  def native_impl = ???

  def native_named_impl = ???
}
