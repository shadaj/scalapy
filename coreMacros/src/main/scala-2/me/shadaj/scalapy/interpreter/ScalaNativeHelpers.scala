package me.shadaj.scalapy.interpreter

import scala.reflect.macros.blackbox
import scala.language.experimental.macros

object ScalaNativeHelpers {
  def inlineFnPtr2(c: blackbox.Context)(fn: c.Tree): c.Tree = {
    import c.universe._

    val out = q"""(null, _root_.scala.scalanative.unsafe.CFuncPtr.toPtr($fn: _root_.scala.scalanative.unsafe.CFuncPtr2[_root_.scala.scalanative.unsafe.Ptr[Byte], _root_.scala.scalanative.unsafe.Ptr[Byte], _root_.scala.scalanative.unsafe.Ptr[Byte]]))"""
    c.untypecheck(out)
  }
}
