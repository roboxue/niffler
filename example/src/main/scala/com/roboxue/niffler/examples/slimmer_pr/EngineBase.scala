package com.roboxue.niffler.examples.slimmer_pr

import com.roboxue.niffler.{DataFlowOperation, Niffler, Token}

/**
  * @author rxue
  * @since 1/1/18.
  */
trait EngineBase {
  this: Niffler =>
  import EngineBase._
  final val scoreDoc: Token[Int] = Token("a score between 0 and 100 where 100 means most similar")
  protected def scoreDocImpl: DataFlowOperation[Int]
  addLogicPart(scoreDocImpl)

  addLogicPart(file1.dependsOn(parsedArgs) { _.file1 })
  addLogicPart(file2.dependsOn(parsedArgs) { _.file2 })
  addLogicPart(stemmer.dependsOn(parsedArgs) { _.stemmer })
  addLogicPart(parsedArgs.dependsOn(Niffler.argv) { (args) =>
    new ArgsUtils(args)
  })
}

object EngineBase extends Niffler {
  final val parsedArgs: Token[ArgsUtils] = Token("parsed argument")
  final val file1: Token[String] = Token("one document")
  final val file2: Token[String] = Token("another document")
  final val stemmer: Token[Stemmer] = Token("a word stemmer")
}
