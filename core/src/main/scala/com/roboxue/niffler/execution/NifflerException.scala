package com.roboxue.niffler.execution
import com.roboxue.niffler.Token

/**
  * @author robert.xue
  * @since 7/15/18
  */
sealed trait NifflerException extends Exception {}

case class NifflerNoDataFlowDefinedException(forToken: Token[_]) extends NifflerException {
  override def getMessage: String = {
    forToken.toString
  }
}

case class NifflerDataFlowExecutionException(forToken: Token[_], ex: Throwable) extends NifflerException {
  // Telling where the token is defined during debugging
  setStackTrace(Array(forToken.stackTrace))

  override def getCause: Throwable = ex

  override def getMessage: String = {
    forToken.toString
  }

}

case object NifflerCancelledException extends NifflerException
