package com.roboxue.niffler.examples.slimmer_pr_control

/**
  * @author rxue
  * @since 1/1/18.
  */
trait EngineBase {

  /**
    * @param doc1 one document
    * @param doc2 another document
    * @return a score between 0 and 100 where 100 means most similar
    */
  def scoreDoc(doc1: String, doc2: String): Int
}
