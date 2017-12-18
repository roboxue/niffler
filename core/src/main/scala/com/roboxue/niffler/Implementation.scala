package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/15/17.
  */
sealed case class Implementation[T](key: Key[T], implementationDetails: ImplementationDetails[T])
