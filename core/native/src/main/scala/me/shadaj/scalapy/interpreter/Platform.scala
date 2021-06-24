package me.shadaj.scalapy.interpreter

import scala.scalanative.{unsafe => sn, libc => lc}
import scala.scalanative.unsafe.Ptr
import java.nio.charset.Charset
import scala.scalanative.unsafe.CQuote

import scala.scalanative.runtime
import scala.scalanative.runtime.Intrinsics
import scala.scalanative.unsigned._

object Platform extends PlatformMacros {
  final val isNative = true

  def Zone[T](fn: sn.Zone => T): T = sn.Zone(fn)

  def fromCString(ptr: Pointer, charset: Charset): String = {
    sn.fromCString(ptr, charset)
  }

  def toCString(str: String, charset: Charset = Charset.defaultCharset())(implicit zone: sn.Zone): CString = sn.toCString(str, charset)

  val emptyCString: CString = c""

  type CString = sn.CString
  type Pointer = Ptr[Byte]
  type PointerToPointer = Ptr[Ptr[Byte]]
  type ThreadLocal[T] = SingleThreadLocal[T]

  def threadLocalWithInitial[T](initial: () => T) = SingleThreadLocal.withInitial(initial)

  def allocPointerToPointer(implicit zone: sn.Zone): PointerToPointer = {
    sn.alloc[Ptr[Byte]]
  }

  def pointerToLong(pointer: Pointer): Long = if (pointer == null) 0 else {
    pointer.toLong
  }

  def cLongToLong(cLong: sn.CLong): Long = cLong
  def cSizeToLong(cSize: sn.CSize): Long = cSize.toLong

  def intToCLong(int: Int): sn.CLong = int
  def intToCSize(int: Int): sn.CSize = int.toULong

  def dereferencePointerToPointer(pointer: PointerToPointer): Pointer = !pointer
  
  def alloc(size: Int): Pointer = {
    lc.stdlib.malloc(size.toULong)
  }

  def ptrSize: Int = sn.sizeof[Ptr[Byte]].toInt

  def setPtrLong(ptr: Pointer, offset: Int, value: Long): Unit = !((ptr + offset)).asInstanceOf[Ptr[Long]] = value
  def setPtrInt(ptr: Pointer, offset: Int, value: Int): Unit = !((ptr + offset)).asInstanceOf[Ptr[Int]] = value
  def setPtrByte(ptr: Pointer, offset: Int, value: Byte): Unit = !((ptr + offset)).asInstanceOf[Ptr[Byte]] = value
}
