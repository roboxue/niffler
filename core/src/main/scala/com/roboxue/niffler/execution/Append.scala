package com.roboxue.niffler.execution

import scala.annotation.implicitNotFound

/**
  * @author rxue
  * @since 7/1/17.
  */
object Append {

  @implicitNotFound(msg = "No implicit for Append.Values[${A}, ${B}] found, so ${B} cannot be appended to ${A}")
  trait Values[A, -B] {
    def appendValues(a: A, b: B): A
  }

  @implicitNotFound(msg = "No implicit for Append.Value[${A}, ${B}] found, so ${B} cannot be appended to ${A}")
  trait Value[A, B] {
    def empty: A

    def appendValue(a: A, b: B): A
  }

  trait Sequence[A, -B, T] extends Value[A, T] with Values[A, B]

  implicit def appendSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] = new Sequence[Seq[T], Seq[V], V] {
    def empty: Seq[T] = Seq.empty

    def appendValues(a: Seq[T], b: Seq[V]): Seq[T] = a ++ b

    def appendValue(a: Seq[T], b: V): Seq[T] = a :+ b
  }

  implicit def appendSeqImplicit[T, V](implicit ev$1: V => T): Sequence[Seq[T], Seq[V], V] =
    new Sequence[Seq[T], Seq[V], V] {
      def empty: Seq[T] = Seq.empty

      def appendValues(a: Seq[T], b: Seq[V]): Seq[T] =
        a ++ (b map { x =>
          x: T
        })

      def appendValue(a: Seq[T], b: V): Seq[T] = a :+ (b: T)
    }

  implicit def appendList[T, V <: T]: Sequence[List[T], List[V], V] = new Sequence[List[T], List[V], V] {
    def empty: List[T] = List.empty

    def appendValues(a: List[T], b: List[V]): List[T] = a ::: b

    def appendValue(a: List[T], b: V): List[T] = a :+ b
  }

  implicit def appendListImplicit[T, V](implicit ev$1: V => T): Sequence[List[T], List[V], V] =
    new Sequence[List[T], List[V], V] {
      def empty: List[T] = List.empty

      def appendValues(a: List[T], b: List[V]): List[T] =
        a ::: (b map { x =>
          x: T
        })

      def appendValue(a: List[T], b: V): List[T] = a :+ (b: T)
    }

  implicit def appendString: Sequence[String, String, String] = new Sequence[String, String, String] {
    def empty: String = ""

    def appendValue(a: String, b: String): String = a + b

    def appendValues(a: String, b: String): String = appendValue(a, b)
  }

  implicit def appendInt: Sequence[Int, Int, Int] = new Sequence[Int, Int, Int] {
    def empty: Int = 0

    def appendValue(a: Int, b: Int): Int = a + b

    def appendValues(a: Int, b: Int): Int = appendValue(a, b)
  }

  implicit def appendLong: Sequence[Long, Long, Long] = new Sequence[Long, Long, Long] {
    def empty: Long = 0L

    def appendValue(a: Long, b: Long): Long = a + b

    def appendValues(a: Long, b: Long): Long = appendValue(a, b)
  }

  implicit def appendDouble: Sequence[Double, Double, Double] = new Sequence[Double, Double, Double] {
    def empty: Double = 0d

    def appendValue(a: Double, b: Double): Double = a + b

    def appendValues(a: Double, b: Double): Double = appendValue(a, b)
  }

  implicit def appendSet[T, V <: T]: Sequence[Set[T], Set[V], V] = new Sequence[Set[T], Set[V], V] {
    def empty: Set[T] = Set.empty

    def appendValues(a: Set[T], b: Set[V]): Set[T] = a ++ b

    def appendValue(a: Set[T], b: V): Set[T] = a + b
  }

  implicit def appendMap[A, B, X <: A, Y <: B]: Sequence[Map[A, B], Map[X, Y], (X, Y)] =
    new Sequence[Map[A, B], Map[X, Y], (X, Y)] {
      def empty: Map[A, B] = Map.empty

      def appendValues(a: Map[A, B], b: Map[X, Y]): Map[A, B] = a ++ b

      def appendValue(a: Map[A, B], b: (X, Y)): Map[A, B] = a + b
    }

}
