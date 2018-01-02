package com.roboxue.niffler.examples.slimmer_pr_control

/**
  * @author rxue
  * @since 1/1/18.
  */
class Engine1(stopWordList: List[String]) extends EngineBase {
  override def scoreDoc(doc1: String, doc2: String, stemmer: Stemmer): Int = {
    // ...magic 1
    1
  }
}
