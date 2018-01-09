package com.roboxue.niffler.examples.slimmer_pr

import com.roboxue.niffler.{DataFlowOperation, Niffler}

/**
  * @author rxue
  * @since 1/1/18.
  */
object Engine3 extends Niffler with EngineBase {
  import EngineBase._
  override def scoreDocImpl: DataFlowOperation[Int] = scoreDoc.dependsOn(file1, file2) { (file1, file2) =>
    // ...magic 3, shouldBe sometime smarter in production
    3
  }
}
