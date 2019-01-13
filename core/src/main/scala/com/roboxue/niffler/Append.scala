package com.roboxue.niffler

import scala.annotation.implicitNotFound

/**
  * Borrowed from SBT, this set of implicit variables can help automated [[com.roboxue.niffler.Token.+=]]
  * @author rxue
  * @since 7/1/17.
  */
object Append {

  @implicitNotFound(msg = "No implicit for Append.Value[${A}, ${B}] found, so ${B} cannot be appended to ${A}")
  trait Value[A, B] {
    def empty: A

    def appendValue(a: A, b: B): A
  }

  @implicitNotFound(msg = "No implicit for Append.Values[${A}, ${B}] found, so ${B} cannot be appended to ${A}")
  trait Values[A, B] {
    def empty: A

    def extendValues(a: A, b: B): A
  }

  implicit def extendSeq[T, V <: T, C[Z] <: Traversable[Z]]: Values[Seq[T], C[V]] = new Values[Seq[T], C[V]] {
    def empty: Seq[T] = Seq.empty

    def extendValues(a: Seq[T], b: C[V]): Seq[T] = a ++ b
  }

  implicit def extendList[T, V <: T, C[Z] <: Traversable[Z]]: Values[List[T], C[V]] = new Values[List[T], C[V]] {
    def empty: List[T] = List.empty

    def extendValues(a: List[T], b: C[V]): List[T] = a ++ b
  }

  implicit def appendSeq[T, V <: T]: Value[Seq[T], V] = new Value[Seq[T], V] {
    def empty: Seq[T] = Seq.empty

    def appendValue(a: Seq[T], b: V): Seq[T] = a :+ b
  }

  implicit def appendList[T, V <: T]: Value[List[T], V] = new Value[List[T], V] {
    def empty: List[T] = List.empty

    def appendValue(a: List[T], b: V): List[T] = a :+ b
  }

  implicit def appendString: Value[String, String] = new Value[String, String] {
    def empty: String = ""

    def appendValue(a: String, b: String): String = a + b
  }

  implicit def appendInt: Value[Int, Int] = new Value[Int, Int] {
    def empty: Int = 0

    def appendValue(a: Int, b: Int): Int = a + b
  }

  implicit def appendLong: Value[Long, Long] = new Value[Long, Long] {
    def empty: Long = 0L

    def appendValue(a: Long, b: Long): Long = a + b
  }

  implicit def appendDouble: Value[Double, Double] = new Value[Double, Double] {
    def empty: Double = 0d

    def appendValue(a: Double, b: Double): Double = a + b
  }

  implicit def appendUnit: Value[Unit, Unit] = new Value[Unit, Unit] {
    def empty: Unit = Unit

    def appendValue(a: Unit, b: Unit): Unit = Unit
  }

  implicit def appendSet[T, V <: T]: Value[Set[T], V] = new Value[Set[T], V] {
    def empty: Set[T] = Set.empty

    def appendValue(a: Set[T], b: V): Set[T] = a + b
  }

  implicit def appendMap[A, B, X <: A, Y <: B]: Value[Map[A, B], (X, Y)] =
    new Value[Map[A, B], (X, Y)] {
      def empty: Map[A, B] = Map.empty

      def appendValue(a: Map[A, B], b: (X, Y)): Map[A, B] = a + b
    }

}
