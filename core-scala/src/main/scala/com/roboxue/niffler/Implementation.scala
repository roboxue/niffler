package com.roboxue.niffler

case class Implementation[T] private (dependencies: Seq[Token[_]], implementation: NifflerSession => T) {
}

object Implementation {
  def apply[T, T1](t1: Token[T1])(impl: T1 => T): Implementation[T] = new Implementation[T](Seq(t1),
    session => {
      impl(session.get(t1))
    })

  def apply[T, T1, T2](t1: Token[T1],
                        t2: Token[T2])
                       (
                         impl: (T1, T2) => T
                       ): Implementation[T] = new Implementation[T](
    Seq(t1, t2),
    session => {
      impl(session.get(t1), session.get(t2))
    })

  def apply[T, T1, T2, T3](
                             t1: Token[T1],
                             t2: Token[T2],
                             t3: Token[T3])
                           (
                             impl: (T1, T2, T3) => T
                           ): Implementation[T] = new Implementation[T](
    Seq(t1, t2, t3),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3))
    })

  def apply[T, T1, T2, T3, T4](
                                 t1: Token[T1],
                                 t2: Token[T2],
                                 t3: Token[T3],
                                 t4: Token[T4])
                               (
                                 impl: (T1, T2, T3, T4) => T
                               ): Implementation[T] = new Implementation[T](
    Seq(t1, t2, t3, t4),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3), session.get(t4))
    })

  def apply[T, T1, T2, T3, T4, T5](
                                     t1: Token[T1],
                                     t2: Token[T2],
                                     t3: Token[T3],
                                     t4: Token[T4],
                                     t5: Token[T5])
                                   (
                                     impl: (T1, T2, T3, T4, T5) => T
                                   ): Implementation[T] = new Implementation[T](
    Seq(t1, t2, t3, t4, t5),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3), session.get(t4), session.get(t5))
    })

}
