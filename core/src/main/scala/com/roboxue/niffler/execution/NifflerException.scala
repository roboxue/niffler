package com.roboxue.niffler.execution

/**
  * @author robert.xue
  * @since 7/15/18
  */
sealed trait NifflerException extends Exception {}

case object NifflerNoDataFlowDefinedException extends NifflerException

case object NifflerCancelledException extends NifflerException
