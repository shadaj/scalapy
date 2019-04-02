package me.shadaj.scalapy.py

import scala.collection.mutable
import scala.reflect.ClassTag

abstract class ObjectWriter[T] {
  def write(v: T): Either[PyValue, Object]
}

object ObjectWriter extends ObjectTupleWriters {
  implicit val pyObjWriter: ObjectWriter[Object] = new ObjectWriter[Object] {
    override def write(v: Object): Either[PyValue, Object] = {
      Right(v)
    }
  }

  implicit val noneWriter: ObjectWriter[None.type] = new ObjectWriter[None.type] {
    override def write(v: None.type): Either[PyValue, Object] = Left(interpreter.noneValue)
  }

  implicit def unionWriter[A, B](implicit aClass: ClassTag[A], bClass: ClassTag[B], aWriter: ObjectWriter[A], bWriter: ObjectWriter[B]): ObjectWriter[A | B] = new ObjectWriter[A | B] {
    override def write(v: A | B): Either[PyValue, Object] = {
      aClass.unapply(v.value) match {
        case Some(a) => aWriter.write(a)
        case _ => bWriter.write(v.value.asInstanceOf[B])
      }
    }
  }

  implicit def pyFascadeWriter[T <: ObjectFacade]: ObjectWriter[T] = new ObjectWriter[T] {
    override def write(v: T): Either[PyValue, Object] = {
      Right(v)
    }
  }

  implicit val pyDynamicObjWriter: ObjectWriter[DynamicObject] = new ObjectWriter[DynamicObject] {
    override def write(v: DynamicObject): Either[PyValue, Object] = {
      Right(v)
    }
  }

  implicit val byteWriter: ObjectWriter[Byte] = new ObjectWriter[Byte] {
    override def write(v: Byte): Either[PyValue, Object] = {
      Left(interpreter.valueFromLong(v))
    }
  }

  implicit val intWriter: ObjectWriter[Int] = new ObjectWriter[Int] {
    override def write(v: Int): Either[PyValue, Object] = {
      Left(interpreter.valueFromLong(v))
    }
  }

  implicit val longWriter: ObjectWriter[Long] = new ObjectWriter[Long] {
    override def write(v: Long): Either[PyValue, Object] = {
      Left(interpreter.valueFromLong(v))
    }
  }

  implicit val doubleWriter: ObjectWriter[Double] = new ObjectWriter[Double] {
    override def write(v: Double): Either[PyValue, Object] = {
      Left(interpreter.valueFromDouble(v))
    }
  }

  implicit val floatWriter: ObjectWriter[Float] = new ObjectWriter[Float] {
    override def write(v: Float): Either[PyValue, Object] = {
      Left(interpreter.valueFromDouble(v))
    }
  }

  implicit val booleanWriter: ObjectWriter[Boolean] = new ObjectWriter[Boolean] {
    override def write(v: Boolean): Either[PyValue, Object] = {
      Left(interpreter.valueFromBoolean(v))
    }
  }

  implicit val stringWriter: ObjectWriter[String] = new ObjectWriter[String] {
    override def write(v: String): Either[PyValue, Object] = {
      Left(interpreter.valueFromString(v))
    }
  }

  private val supportedObjectTypes = Set[Class[_]](classOf[String])
  implicit def seqWriter[T: ClassTag, C](implicit ev1: C => Seq[T], tWriter: ObjectWriter[T]): ObjectWriter[C] = new ObjectWriter[C] {
    override def write(v: C): Either[PyValue, Object] = {
      if (Platform.isNative) {
        val writtenElems = v.view.map { e =>
          tWriter.write(e)
        }
        
        val writtenObjects = writtenElems.map(_.left.map(Object.populateWith).merge.expr)
          Right(Object(writtenObjects.mkString("[", ",", "]")))

        Right(Object(writtenObjects.mkString("[", ",", "]")))
      } else {
        val elemClass = implicitly[ClassTag[T]].runtimeClass
        def isNativeWritable(clazz: Class[_]): Boolean = {
          clazz.isPrimitive || supportedObjectTypes.contains(clazz) ||
            (clazz.isArray && isNativeWritable(clazz.getComponentType))
        }

        if (isNativeWritable(elemClass)) {
          if (v.isInstanceOf[Array[_]]) {
            Left(interpreter.asInstanceOf[JepInterpreter].valueFromAny(v))
          } else {
            Left(interpreter.asInstanceOf[JepInterpreter].valueFromAny(v.toArray[T]))
          }
        } else {
          val writtenElems = v.view.map { e =>
            tWriter.write(e)
          }

          if (writtenElems.forall(_.isLeft)) Left(
            interpreter.asInstanceOf[JepInterpreter].valueFromAny(
              writtenElems.map(_.left.get).toArray
            )
          )
          else {
            val writtenObjects = writtenElems.map(_.left.map(Object.populateWith).merge.expr)
            Right(Object(writtenObjects.mkString("[", ",", "]")))
          }
        }
      }
    }
  }

  implicit def mapWriter[I, O](implicit iWriter: ObjectWriter[I], oWriter: ObjectWriter[O]) = new ObjectWriter[Map[I, O]] {
    override def write(map: Map[I, O]): Either[PyValue, Object] = {
      val toAddLater = mutable.Queue.empty[(Object, Object)]

      map.foreach { case (i, o) =>
        (iWriter.write(i), oWriter.write(o)) match {
          case (Left(k), Left(v)) =>
            toAddLater.enqueue((Object.populateWith(k), Object.populateWith(v)))
          case (Left(k), Right(vo)) => toAddLater.enqueue((Object.populateWith(k), vo))
          case (Right(ko), Left(v)) => toAddLater.enqueue((ko, Object.populateWith(v)))
          case (Right(ko), Right(vo)) => toAddLater.enqueue((ko, vo))
        }
      }

      val obj = Object("{}")
      toAddLater.foreach { case (ko, vo) =>
        interpreter.eval(s"${obj.expr}[${ko.expr}] = ${vo.expr}")
      }

      Right(obj)
    }
  }
}
