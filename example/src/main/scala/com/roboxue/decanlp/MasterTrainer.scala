package com.roboxue.decanlp
import java.nio.file.Paths

import com.roboxue.niffler.{ExecutionState, ExecutionStateTracker, Token}

object MasterTrainer {

  /**
    * all deca nlp challenges to be trained in this training
    *
    * @return
    */
  def decaTasks: Seq[DecaTask] = {
    ???
  }

  val dataLoader = new DataLoader(decaTasks)

  val trainingRounds: Token[Int] = Token("number of rounds of training")
  val kickOffTraining: Token[Unit] = Token("kick off training")
  val fullWordEmbeddings: Token[Map[String, Embeddings]] = Token("effective embeddings in this round of training")
  val tokenizer: Token[Sentence => Seq[String]] = Token("tokenizer")
  val generativeWordEmbeddings: Token[Seq[Word]] = Token("generative words available to use when generating answers")
  val modelSnapshot: Token[ModelSnapshot] = Token("model state")

  kickOffTraining
    .dependsOn(modelSnapshot, generativeWordEmbeddings, fullWordEmbeddings, tokenizer)
    .implBy({ (model, words, embeddings, tokenizer) =>
      implicit val stateTracker: ExecutionStateTracker = new ExecutionStateTracker()
      for (task <- decaTasks) {
        dataLoader.datasetTokenForTask(task)
        BatchTrainer.asyncRun(
          BatchTrainer.performTraining,
          Seq(
            BatchTrainer.modelStartState := model,
            BatchTrainer.saveSnapshotEvery := 5,
            BatchTrainer.modelSavingPath := Paths.get(""), // FIXME
            BatchTrainer.batchData.dependsOn(dataLoader.datasetTokenForTask(task)).implBy(_.batchIterator)
          )
        )
      }
    })

  def main(args: Array[String]): Unit = {
    var round = 0
  }

}
