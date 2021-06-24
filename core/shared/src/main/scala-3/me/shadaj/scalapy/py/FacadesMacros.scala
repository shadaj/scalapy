package me.shadaj.scalapy.py

trait FacadesMacros {
  implicit def getCreator[F <: Any]: FacadeCreator[F] = ${FacadeImpl.creator[F]}
}

trait PyMacros {
  def native[T]: T = ${FacadeImpl.native_impl[T]}
  def nativeNamed[T]: T = ${FacadeImpl.native_named_impl[T]}
}