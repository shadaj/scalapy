package me.shadaj.scalapy.py

import scala.reflect.macros.whitebox
import scala.language.experimental.macros

import scala.annotation.StaticAnnotation
class native extends StaticAnnotation

object FacadeImpl {
  def creator[T <: Any](c: whitebox.Context)(implicit tag: c.WeakTypeTag[T]): c.Tree = {
    import c.universe._

    if (!tag.tpe.typeSymbol.annotations.exists(_.tpe =:= typeOf[native])) {
      c.error(c.enclosingPosition, "Cannot derive a creator for a trait that is not annotated as @py.native")
    }
    
    q"""new _root_.me.shadaj.scalapy.py.FacadeCreator[${tag.tpe}] {
      def create(value: _root_.me.shadaj.scalapy.interpreter.PyValue) = new _root_.me.shadaj.scalapy.py.FacadeValueProvider(value) with ${tag.tpe} {}
    }"""
  }

  def native_impl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[T] = {
    import c.universe._

    if (!c.enclosingClass.symbol.annotations.exists(_.tpe =:= typeOf[native])) {
      c.error(c.enclosingPosition, "py.native implemented functions can only be declared inside traits annotated as @py.native")
    }

    val method = c.internal.enclosingOwner.asMethod
    val methodName = method.name.toString
    val returnType = method.returnType
    val paramss = method.paramLists

    paramss.headOption match {
      case Some(params) =>
        val paramExprs = params.map(_.name)
        c.Expr[T](q"as[_root_.me.shadaj.scalapy.py.Dynamic].applyDynamic($methodName)(..$paramExprs).as[$returnType]")
      case scala.None =>
        c.Expr[T](q"as[_root_.me.shadaj.scalapy.py.Dynamic].selectDynamic($methodName).as[$returnType]")
    }
  }

  def native_named_impl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[T] = {
    import c.universe._

    if (!c.enclosingClass.symbol.annotations.exists(_.tpe =:= typeOf[native])) {
      c.error(c.enclosingPosition, "py.native implemented functions can only be declared inside traits annotated as @py.native")
    }

    val method = c.internal.enclosingOwner.asMethod
    val methodName = method.name.toString
    val returnType = method.returnType
    val paramss = method.paramLists

    paramss.headOption match {
      case Some(params) =>
        val paramExprs = params.map { p =>
          val paramName = q"${p.asTerm}".toString
          s"""("${p.name}", $paramName)"""
        }

        c.Expr[T](c.parse(s"""as[_root_.me.shadaj.scalapy.py.Dynamic].applyDynamicNamed("$methodName")(${paramExprs.mkString(",")}).as[$returnType]"""))

      case scala.None =>
        c.Expr[T](q"as[_root_.me.shadaj.scalapy.py.Dynamic].selectDynamic($methodName).as[$returnType]")
    }
  }
}