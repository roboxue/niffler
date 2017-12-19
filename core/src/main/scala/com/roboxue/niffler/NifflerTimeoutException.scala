package com.roboxue.niffler

import scala.concurrent.duration.FiniteDuration

/**
  * @author rxue
  * @since 12/19/17.
  */
case class NifflerTimeoutException(executionSnapshot: ExecutionSnapshot, timeout: FiniteDuration) extends Exception
