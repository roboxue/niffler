package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/24/17.
  */
trait Niffler {
  implicit protected val implementations: ImplementationCollection = new ImplementationCollection()

}
