package me.shadaj.scalapy.interpreter

import scala.scalanative.unsafe.Ptr
import scala.language.experimental.macros

trait PlatformMacros {
  def getFnPtr2(fn: (Ptr[Byte], Ptr[Byte]) => Ptr[Byte]): (scala.Any, Ptr[Byte]) = macro ScalaNativeHelpers.inlineFnPtr2
}