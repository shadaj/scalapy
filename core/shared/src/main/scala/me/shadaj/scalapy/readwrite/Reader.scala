package me.shadaj.scalapy.readwrite

import scala.reflect.ClassTag

import me.shadaj.scalapy.interpreter.PyValue
import me.shadaj.scalapy.py
import me.shadaj.scalapy.py.FacadeCreator

import scala.collection.mutable
import scala.collection.compat._
import me.shadaj.scalapy.interpreter.Platform
import me.shadaj.scalapy.interpreter.CPythonInterpreter
import me.shadaj.scalapy.interpreter.CPythonAPI

trait Reader[T] {
  // no guarantees
  def read(r: PyValue): T = {
    CPythonInterpreter.withGil(readNative(r.underlying))
  }

  // borrowed, no references should be leaded without a ref count bump
  // assumes that GIL is held
  def readNative(r: Platform.Pointer): T = {
    read(PyValue.fromBorrowed(r))
  }
}

object Reader extends TupleReaders with FunctionReaders {
  implicit val anyReader: Reader[py.Any] = new Reader[py.Any] {
    override def readNative(r: Platform.Pointer): py.Any = py.Any.populateWith(PyValue.fromBorrowed(r))
  }

  implicit def facadeReader[F <: py.Any](implicit creator: FacadeCreator[F]): Reader[F] = new Reader[F] {
    override def readNative(r: Platform.Pointer): F = creator.create(PyValue.fromBorrowed(r))
  }

  implicit val unitReader: Reader[Unit] = new Reader[Unit] {
    override def readNative(r: Platform.Pointer): Unit = ()
  }

  implicit val byteReader: Reader[Byte] = new Reader[Byte] {
    override def readNative(r: Platform.Pointer): Byte = {
      val res = CPythonAPI.PyLong_AsLongLong(r)
      if (res == -1) {
        CPythonInterpreter.throwErrorIfOccured()
      }

      res.toByte
    }
  }

  implicit val intReader: Reader[Int] = new Reader[Int] {
    override def readNative(r: Platform.Pointer): Int = {
      val res = CPythonAPI.PyLong_AsLongLong(r)
      if (res == -1) {
        CPythonInterpreter.throwErrorIfOccured()
      }

      res.toInt
    }
  }

  implicit val longReader: Reader[Long] = new Reader[Long] {
    override def readNative(r: Platform.Pointer): Long = {
      val res = CPythonAPI.PyLong_AsLongLong(r)
      if (res == -1) {
        CPythonInterpreter.throwErrorIfOccured()
      }

      res
    }
  }

  implicit val doubleReader: Reader[Double] = new Reader[Double] {
    override def readNative(r: Platform.Pointer): Double = {
      val res = CPythonAPI.PyFloat_AsDouble(r)
      if (res == -1.0) {
        CPythonInterpreter.throwErrorIfOccured()
      }

      res
    }
  }

  implicit val floatReader: Reader[Float] = new Reader[Float] {
    override def readNative(r: Platform.Pointer): Float = {
      val res = CPythonAPI.PyFloat_AsDouble(r)
      if (res == -1.0) {
        CPythonInterpreter.throwErrorIfOccured()
      }

      res.toFloat
    }
  }

  implicit val booleanReader: Reader[Boolean] = new Reader[Boolean] {
    override def readNative(r: Platform.Pointer): Boolean = {
      if (r == CPythonInterpreter.falseValue.underlying) false
      else if (r == CPythonInterpreter.trueValue.underlying) true
      else {
        throw new IllegalAccessException("Cannot convert a non-boolean value to a boolean")
      }
    }
  }

  implicit val stringReader: Reader[String] = new Reader[String] {
    override def readNative(r: Platform.Pointer): String = {
      val cStr = CPythonAPI.PyUnicode_AsUTF8(r)
      CPythonInterpreter.throwErrorIfOccured()
      Platform.fromCString(cStr, java.nio.charset.Charset.forName("UTF-8"))
    }
  }

  implicit val charReader: Reader[Char] = new Reader[Char] {
    override def readNative(r: Platform.Pointer): Char = {
      val rStr = stringReader.readNative(r)
      if (rStr.length != 1) {
        throw new IllegalArgumentException("Cannot extract a char from a string with length != 1")
      } else {
        rStr.head
      }
    }
  }

  implicit def mutableSeqReader[T](implicit reader: Reader[T], writer: Writer[T]): Reader[mutable.Seq[T]] = new Reader[mutable.Seq[T]] {
    override def read(r: PyValue) = r.dup().getSeq(reader.readNative, writer.writeNative)
  }

  implicit def seqReader[T, C[A] <: Iterable[A]](implicit reader: Reader[T], bf: Factory[T, C[T]]): Reader[C[T]] = new Reader[C[T]] {
    override def readNative(r: Platform.Pointer) = {
      val length = Platform.cSizeToLong(CPythonAPI.PySequence_Length(r)).toInt
      CPythonInterpreter.throwErrorIfOccured()

      val builder = bf.newBuilder
      builder.sizeHint(length)

      (0 until length).foreach { i =>
        val ret = CPythonAPI.PySequence_GetItem(r, i)
        CPythonInterpreter.throwErrorIfOccured()
        try {
          builder += reader.readNative(ret)
        } finally {
          CPythonAPI.Py_DecRef(ret)
        }
      }

      builder.result()
    }
  }

  implicit def mapReader[I, O](implicit readerI: Reader[I], readerO: Reader[O]): Reader[Map[I, O]] = new Reader[Map[I, O]] {
    override def read(r: PyValue): Map[I, O] = {
      r.dup().getMap.map { case (k, v) =>
        readerI.read(k) -> readerO.read(v)
      }.toMap
    }
  }
}
