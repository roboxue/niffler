package com.roboxue.niffler

import com.roboxue.niffler.execution.{AsyncExecution, ExecutionLogger}
import javax.annotation.Nullable

import scala.collection.JavaConverters._
/**
  * @author robert.xue
  * @since 2019-04-01
  */
object LanguageBridge {
  def asyncRun[T](niffler: javaDSL.Niffler,
                 token: Token[T],
                  extraFlow: java.lang.Iterable[DataFlow[_]],
                  sc: ExecutionStateTracker,
                  @Nullable logger: ExecutionLogger): AsyncExecution[T] = {
    new Logic(niffler.getDataFlows.asScala).asyncRun(token, extraFlow.asScala, Option(logger))(sc)
  }

  def javaIterableAsSeq[T](iterable: java.lang.Iterable[T]): Seq[T] = {
    iterable.asScala.toSeq
  }
}
