package me.shadaj.scalapy.readwrite

import scala.collection.mutable
import scala.reflect.ClassTag

import me.shadaj.scalapy.interpreter.{CPythonInterpreter, PyValue, Platform}
import me.shadaj.scalapy.py
import me.shadaj.scalapy.py.|
import me.shadaj.scalapy.py.PyQuote
import me.shadaj.scalapy.interpreter.CPythonAPI
import CPythonInterpreter.withGil

abstract class Writer[T] {
  // no guarantees about references
  def write(v: T): PyValue = withGil(PyValue.fromNew(writeNative(v)))

  // always returns a PyValue that is owned by the caller and has no other references
  // assumes that the GIL is held
  def writeNative(v: T): Platform.Pointer = {
    val written = write(v)
    CPythonAPI.Py_IncRef(written.underlying)
    written.underlying
  }
}

object Writer extends TupleWriters with FunctionWriters {
  implicit def anyWriter[T <: py.Any]: Writer[T] = new Writer[T] {
    override def writeNative(v: T): Platform.Pointer = {
      CPythonAPI.Py_IncRef(v.value.underlying)
      v.value.underlying
    }
  }

  implicit def unionWriter[A, B](implicit aClass: ClassTag[A], bClass: ClassTag[B], aWriter: Writer[A], bWriter: Writer[B]): Writer[A | B] = new Writer[A | B] {
    override def writeNative(v: A | B): Platform.Pointer = {
      aClass.unapply(v.value) match {
        case Some(a) => aWriter.writeNative(a)
        case _ => bWriter.writeNative(v.value.asInstanceOf[B])
      }
    }
  }

  implicit val unitWriter: Writer[Unit] = new Writer[Unit] {
    override def writeNative(v: Unit): Platform.Pointer = {
      CPythonAPI.Py_IncRef(CPythonInterpreter.noneValue.underlying)
      CPythonInterpreter.noneValue.underlying
    }
  }

  implicit val byteWriter: Writer[Byte] = new Writer[Byte] {
    override def writeNative(v: Byte): Platform.Pointer = CPythonAPI.PyLong_FromLongLong(v)
  }

  implicit val intWriter: Writer[Int] = new Writer[Int] {
    override def writeNative(v: Int): Platform.Pointer = CPythonAPI.PyLong_FromLongLong(v)
  }

  implicit val longWriter: Writer[Long] = new Writer[Long] {
    override def writeNative(v: Long): Platform.Pointer = CPythonAPI.PyLong_FromLongLong(v)
  }

  implicit val doubleWriter: Writer[Double] = new Writer[Double] {
    override def writeNative(v: Double): Platform.Pointer = CPythonAPI.PyFloat_FromDouble(v)
  }

  implicit val floatWriter: Writer[Float] = new Writer[Float] {
    override def writeNative(v: Float): Platform.Pointer = CPythonAPI.PyFloat_FromDouble(v)
  }

  implicit val booleanWriter: Writer[Boolean] = new Writer[Boolean] {
    override def writeNative(v: Boolean): Platform.Pointer = CPythonAPI.PyBool_FromLong(Platform.intToCLong(if (v) 1 else 0))
  }

  implicit val stringWriter: Writer[String] = new Writer[String] {
    override def writeNative(v: String): Platform.Pointer = CPythonInterpreter.toNewString(v)
  }

  implicit def mapWriter[K, V](implicit kWriter: Writer[K], vWriter: Writer[V]): Writer[Map[K, V]] = new Writer[Map[K, V]] {
    override def write(map: Map[K, V]): PyValue = {
      val obj = CPythonInterpreter.newDictionary()
      map.foreach { case (k, v) =>
        CPythonInterpreter.updateBracket(obj, kWriter.write(k), vWriter.write(v))
      }

      obj
    }
  }
}
