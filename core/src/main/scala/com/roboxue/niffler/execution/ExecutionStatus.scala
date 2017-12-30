package com.roboxue.niffler.execution

/**
  * @author rxue
  * @since 12/30/17.
  */
sealed trait ExecutionStatus

object ExecutionStatus {

  case object Unstarted extends ExecutionStatus

  case object Running extends ExecutionStatus

  case object Cancelled extends ExecutionStatus

  case object Completed extends ExecutionStatus

  case object Failed extends ExecutionStatus

}
