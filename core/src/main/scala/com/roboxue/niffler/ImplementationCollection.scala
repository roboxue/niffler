package com.roboxue.niffler

import monix.eval.Coeval

import scala.annotation.implicitNotFound
import scala.collection.mutable.ListBuffer

/**
  * @author rxue
  * @since 12/24/17.
  */
@implicitNotFound(msg = "please write implementation in a class that extends com.roboxue.niffler.Niffler trait")
class ImplementationCollection {
  private val implementations = ListBuffer.empty[Coeval[Implementation[_]]]

  def addImpl(impl: => Implementation[_]): Unit = {
    implementations += Coeval(impl)
  }

  def collectImplementations: Iterable[Implementation[_]] = {
    implementations.toList.map(_.apply())
  }
}
