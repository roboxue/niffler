package com.roboxue.niffler

import java.io.File

case class Implementation[T] private(dependencies: Seq[Token[_]], implementation: NifflerSession => T,
                                     name: String, line: Int, className: String) {
}

object Implementation {
  def apply[T, T1](t1: Token[T1])(impl: T1 => T)(
    implicit line: sourcecode.Line, file: sourcecode.File, pkg: sourcecode.Pkg, name: sourcecode.Name
  ): Implementation[T] = new Implementation[T](Seq(t1),
    session => {
      impl(session.get(t1))
    }, name.value, line.value, s"${pkg.value.replace('.', '/')}/${file.value.split(File.separatorChar).last}")

  def apply[T, T1, T2](t1: Token[T1],
                       t2: Token[T2])
                      (
                        impl: (T1, T2) => T
                      )(
                        implicit line: sourcecode.Line, file: sourcecode.File, pkg: sourcecode.Pkg, name: sourcecode.Name
                      ): Implementation[T] = new Implementation[T](
    Seq(t1, t2),
    session => {
      impl(session.get(t1), session.get(t2))
    }, name.value, line.value, s"${pkg.value.replace('.', '/')}/${file.value.split(File.separatorChar).last}")

  def apply[T, T1, T2, T3](
                            t1: Token[T1],
                            t2: Token[T2],
                            t3: Token[T3])
                          (
                            impl: (T1, T2, T3) => T
                          )(
                            implicit line: sourcecode.Line, file: sourcecode.File, pkg: sourcecode.Pkg, name: sourcecode.Name
                          ): Implementation[T] = new Implementation[T](
    Seq(t1, t2, t3),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3))
    }, name.value, line.value, s"${pkg.value.replace('.', '/')}/${file.value.split(File.separatorChar).last}")

  def apply[T, T1, T2, T3, T4](
                                t1: Token[T1],
                                t2: Token[T2],
                                t3: Token[T3],
                                t4: Token[T4])
                              (
                                impl: (T1, T2, T3, T4) => T
                              )(
                                implicit line: sourcecode.Line, file: sourcecode.File, pkg: sourcecode.Pkg, name: sourcecode.Name
                              ): Implementation[T] = new Implementation[T](
    Seq(t1, t2, t3, t4),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3), session.get(t4))
    }, name.value, line.value, s"${pkg.value.replace('.', '/')}/${file.value.split(File.separatorChar).last}")

  def apply[T, T1, T2, T3, T4, T5](
                                    t1: Token[T1],
                                    t2: Token[T2],
                                    t3: Token[T3],
                                    t4: Token[T4],
                                    t5: Token[T5])
                                  (
                                    impl: (T1, T2, T3, T4, T5) => T
                                  )(
                                    implicit line: sourcecode.Line, file: sourcecode.File, pkg: sourcecode.Pkg, name: sourcecode.Name
                                  ): Implementation[T] = new Implementation[T](
    Seq(t1, t2, t3, t4, t5),
    session => {
      impl(session.get(t1), session.get(t2), session.get(t3), session.get(t4), session.get(t5))
    }, name.value, line.value, s"${pkg.value.replace('.', '/')}/${file.value.split(File.separatorChar).last}")

}
