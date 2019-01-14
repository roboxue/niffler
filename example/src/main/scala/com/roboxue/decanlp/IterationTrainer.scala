package com.roboxue.decanlp
import java.nio.file.Path

import com.roboxue.niffler.{DataFlow, Logic, Niffler, Token}

object IterationTrainer extends Niffler {
  val modelStartState: Token[ModelSnapshot] = Token("model state before this iteration of training")
  val vocabulary: Token[Seq[Word]] = Token("the full list of vocabulary available for generating answer")
  val iterationTrainingData: Token[Seq[DecaIndexedInput]] = Token(
    "a collection of preprocessed question and contexts." +
      "they need to have to be aligned and padded to the same length"
  )
  val iterationLearningRate: Token[Float] = Token("the learning rate of this iteration of training")
  val performTraining: Token[ModelSnapshot] = Token("finished a round of training")

  val iterationId: Token[Int] = Token("the id of this round of training")
  val modelSavingPath: Token[Path] = Token("the directory to save model")
  val saveSnapshot: Token[Path] = Token("save model state to file system")

  override def dataFlows: Iterable[DataFlow[_]] = Seq(
    performTraining
      .dependsOn(modelStartState, iterationLearningRate, iterationTrainingData, vocabulary)
      .implBy({ (model, lr, data, vocab) =>
        ???
      }),
    saveSnapshot
      .dependsOn(modelSavingPath, iterationId)
      .implBy({ (basePath, id) =>
        val actualSavingPath = basePath.resolve(f"iteration-$id%05d")
        // TODO: JNI Save
        actualSavingPath
      })
  )

}
