package com.roboxue.decanlp
import java.nio.file.Path

import com.roboxue.niffler._

import scala.concurrent.duration.Duration

object BatchTrainer extends Niffler {
  val modelStartState: Token[ModelSnapshot] = Token("model state before this iteration of training")
  val batchData: Token[Iterator[MiniBatch]] = Token("mini batch iterator")
  val maxNumberOfIterations: Token[Int] = Token(
    "complete the batch of training after at most this many rounds of iteration"
  )
  val saveSnapshotEvery: Token[Int] = Token("save snapshot after several iterations")
  val modelSavingPath: Token[Path] = Token("the directory to save model")
  val performTraining: Token[ModelSnapshot] = Token("finished a round of training")

  override val logic: Logic = new Logic(
    Seq(
      saveSnapshotEvery := 5,
      performTraining
        .dependsOn(modelStartState, batchData, maxNumberOfIterations, saveSnapshotEvery, modelSavingPath)
        .implBy({ (model, batch, maxIterations, saveInterval, saveFolder) =>
          // Use an jvm optimizer to get this
          val dummyLearningRate = 0.0001f
          var iteration = 0
          var loss = 0.0f
          while (batch.hasNext && iteration < maxIterations) {
            implicit val stateTracker: ExecutionStateTracker = new ExecutionStateTracker(
              ExecutionState.emptyMutable
                .put(IterationTrainer.iterationId, iteration)
                .put(IterationTrainer.iterationLearningRate, dummyLearningRate)
                .put(IterationTrainer.modelStartState, model)
                .put(IterationTrainer.modelSavingPath, saveFolder)
                .seal
            )
            val trainingResult = IterationTrainer.asyncRun(IterationTrainer.performTraining).awaitValue(Duration.Inf)
            iteration += 1
            if (iteration % saveInterval == 0) {
              IterationTrainer.asyncRun(IterationTrainer.saveSnapshot)
            }
            loss += trainingResult.loss
          }
          model.copy(loss = loss)
        })
    )
  )

}
