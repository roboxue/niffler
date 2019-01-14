package com.roboxue.decanlp
import java.nio.file.Paths

import akka.actor.ActorSystem
import com.roboxue.niffler.Niffler

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object MainUserInterface {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem = ActorSystem.create("example")
    val decaTasks = Seq(
      DecaTask.QuestionAnswering,
      DecaTask.Summarization,
      DecaTask.SemanticParsing,
      DecaTask.SemanticRoleLabeling,
      DecaTask.CommonsenseReasoning,
      DecaTask.GoalOrientedDialogue,
      DecaTask.NaturalLanguageInference,
      DecaTask.RelationExtraction,
      DecaTask.NamedEntityRecognition,
      DecaTask.SentimentAnalysis,
      DecaTask.MachineTranslation("de", "en"),
      DecaTask.MachineTranslation("en", "de"),
    )

    val downloader: Niffler = new DataDownload(decaTasks)
    val dataLoader: Niffler = new DataLoader(decaTasks)
    val finalProduct = downloader ++ dataLoader
    finalProduct
      .asyncRun(
        DataLoader.prepareAllData,
        Seq(
          DataDownload.dataPath := Paths.get("/tmp/decanlp/raw"),
          DataLoader.workingDirectory := Paths.get("/tmp/decanlp/splits")
        )
      )
      .withAkka(system)
      .future
      .onComplete({
        case Success(r) =>
          println(r.value)
          println("========================")
          r.executionLog.logLines.foreach(println)
          println("========================")
          r.executionLog.printFlowChart(println)
          system.terminate()
        case Failure(ex) =>
          ex.printStackTrace()
          system.terminate()
      })(ExecutionContext.global)

  }
}
