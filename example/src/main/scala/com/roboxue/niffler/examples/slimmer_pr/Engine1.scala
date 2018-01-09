package com.roboxue.niffler.examples.slimmer_pr

import com.roboxue.niffler.{DataFlowOperation, Niffler, Token}

/**
  * @author rxue
  * @since 1/1/18.
  */
object Engine1 extends Niffler with EngineBase {
  final val stopWordList: Token[List[String]] = Token(
    "a stop word list marking common vocabulary that should be ignored"
  )
  addLogicPart(stopWordList.dependsOn(EngineBase.parsedArgs) { _.stopWordList })

  import EngineBase._
  override def scoreDocImpl: DataFlowOperation[Int] = scoreDoc.dependsOn(file1, file2, stopWordList, stemmer) {
    (file1, file2, stopWordList, stemmer) =>
      // ...magic 1, shouldBe sometime smarter in production
      1
  }
}
