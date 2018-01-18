package com.roboxue.niffler.examples

import com.roboxue.niffler.monitoring.{ExecutionHistoryService, NifflerMonitor}
import com.roboxue.niffler.syntax.{Constant, Requires}
import com.roboxue.niffler.{Logic, Niffler, Token}

/**
  * @author rxue
  * @since 12/30/17.
  */
object NifflerMonitorDemo {
  def main(args: Array[String]): Unit = {
    // A monitoring server with ExecutionHistoryService as a plugin
    val logic1 = Niffler.combine(NifflerMonitor, ExecutionHistoryService)
    // a logic that will throw exception in a prerequisite step
    val logic2 = logic1.diverge(Iterable(NifflerMonitor.nifflerMonitorServicePortNumber := Constant({
      throw new Exception("hello world niffler")
    })))

    // trigger execution in sync fashion
    val result1 = logic1.syncRun(NifflerMonitor.nifflerMonitorStartServer)
    // open localhost:4080/history now to see a web ui
    // same logic can trigger multiple executions, in async fashion as well
    logic1.asyncRun(NifflerMonitor.nifflerMonitorService)
    // same logic can trigger multiple executions with some cache from previous execution
    logic1.asyncRun(NifflerMonitor.nifflerMonitorService, cache = result1.cacheAfterExecution)

    logic2.asyncRun(NifflerMonitor.nifflerMonitorStartServer)

    // write new logic from scratch is totally possible
    val t1 = Token[String]("t1")
    val t2 = Token[Int]("t2")
    val t3 = Token[Int]("t3")
    val t4 = Token[Int]("t4")
    val t5 = Token[Int]("t5")
    val logic3 = Logic(Iterable(t1 := Constant({
      Thread.sleep(10000)
      "good morning"
    }), t2 := Constant({
      Thread.sleep(4000)
      2
    }), t3 := Constant({
      Thread.sleep(20000)
      3
    }), t4 := Requires(t1, t2) { _.length + _ }, t5 := Requires(t4, t3) { _ + _ }))
    logic3.asyncRun(t5)

    // missing implementation for a token will yield a quick runtime exception
    val logic4 = Logic(Iterable(t1 := Constant({
      Thread.sleep(10000)
      "good morning"
    }), t2 := Constant({
      Thread.sleep(4000)
      2
    }), t3 := Constant({
      Thread.sleep(20000)
      3
    }), t5 := Requires(t4, t3) { _ + _ }))
    logic4.asyncRun(t5)
  }
}
