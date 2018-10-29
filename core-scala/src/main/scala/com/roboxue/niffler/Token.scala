package com.roboxue.niffler

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.runtime.universe.typeOf

case class Token[T: TypeTag](codeName: String, uuid: String, summaryName: String, description: String) {
  type T0 = T
  val typeName: String = typeOf[T].toString

  def dependsOn[T1](t1: Token[T1])(impl: T1 => T): Implementation[T] = new Implementation[T](this, Seq(t1),
    session => {
      impl(session.get(t1))
    })

  def dependsOn[T1, T2](t1: Token[T1],
                        t2: Token[T2])
                       (
                         impl: (T1, T2) => T
                       ): Implementation[T] = new Implementation[T](
    this,
    Seq(t1, t2),
    session => {
      impl(session.get(t1), session.get(t2))
    })

  def dependsOn[T1, T2, T3](
                             t1: Token[T1],
                             t2: Token[T2],
                             t3: Token[T3])
                           (
                             impl: (T1, T2, T3) => T
                           ): Implementation[T] = new Implementation[T](
    this,
    Seq(t1, t2, t3),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3))
    })

  def dependsOn[T1, T2, T3, T4](
                                 t1: Token[T1],
                                 t2: Token[T2],
                                 t3: Token[T3],
                                 t4: Token[T4])
                               (
                                 impl: (T1, T2, T3, T4) => T
                               ): Implementation[T] = new Implementation[T](
    this,
    Seq(t1, t2, t3, t4),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3), session.get(t4))
    })

  def dependsOn[T1, T2, T3, T4, T5](
                                     t1: Token[T1],
                                     t2: Token[T2],
                                     t3: Token[T3],
                                     t4: Token[T4],
                                     t5: Token[T5])
                                   (
                                     impl: (T1, T2, T3, T4, T5) => T
                                   ): Implementation[T] = new Implementation[T](
    this,
    Seq(t1, t2, t3, t4, t5),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3), session.get(t4), session.get(t5))
    })
}
