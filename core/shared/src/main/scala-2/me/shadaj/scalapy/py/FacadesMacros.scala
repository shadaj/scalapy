package me.shadaj.scalapy.py

import scala.language.experimental.macros

trait FacadesCreatorMacros {
  implicit def getCreator[F <: Any]: FacadeCreator[F] = macro FacadeImpl.creator[F]
}

trait PyMacros {
  def native[T]: T = macro FacadeImpl.native_impl[T]
  def nativeNamed[T]: T = macro FacadeImpl.native_named_impl[T]
}