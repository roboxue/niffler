package com.roboxue.niffler

/**
  * @author rxue
  * @since 1/18/18.
  */
package object syntax {

  object Constant {
    def apply[T](constant: => T): Formula[T] = {
      Formula(Set.empty, (cache) => constant)
    }
  }

}
