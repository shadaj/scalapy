package me.shadaj.scalapy.py

import scala.language.experimental.macros

import me.shadaj.scalapy.interpreter.PyValue

class FacadeValueProvider(private[scalapy] val rawValue: PyValue) extends Any

class StaticModule(name: String) extends Module {
  private[scalapy] val rawValue = module(name).value
}

class StaticValue(value: Any) extends Any {
  private[scalapy] val rawValue = value.value
}

abstract class FacadeCreator[F <: Any] {
  def create(value: PyValue): F
}

object FacadeCreator extends FacadesCreatorMacros {}
