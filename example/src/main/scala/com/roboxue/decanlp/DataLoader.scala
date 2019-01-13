package com.roboxue.decanlp
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap

import com.roboxue.decanlp.DataLoader.WordCount
import com.roboxue.niffler.{DataFlow, Logic, Niffler, Token}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class DataLoader(decaTasks: Seq[DecaTask]) extends Niffler {
  val minOccurrenceRequirement: Token[Int] = Token(
    "the minimum amount of occurrence required to be counted as a vocabulary"
  )
  val wordCounts: Token[WordCount] = Token("the word count stats for all data")
  val sharedVocabulary: Token[Seq[String]] = Token("list of words sorted by their frequency desc")

  private val dynamicTokens = ListBuffer[Token[DataLoader.WordCount]]()
  private val logicBuffer = ListBuffer[DataFlow[DataLoader.WordCount]]()

  for (task <- decaTasks) {
    task match {
      case DecaTask.QuestionAnswering =>
        val t = Token[DataLoader.WordCount]("squad train data word count", "squadTrainingSetWordCount")
        dynamicTokens += t
        logicBuffer += t.dependsOn(SquadLoader.tokenizedTrainData).implBy(DataLoader.wordCounts)
    }
  }

  override val logic: Logic = new Logic(
    logicBuffer ++ Seq(
      wordCounts
        .dependsOnAllOf(dynamicTokens: _*)
        .implBy({ wordCounts =>
          val cache = new ConcurrentHashMap[String, Long]()
          wordCounts.par.fold(cache) { (cache, record) =>
            for (k <- record.keys.asScala) {
              cache.merge(k, 0, _ + _)
            }
            cache
          }
          cache
        }),
      sharedVocabulary
        .dependsOn(wordCounts, minOccurrenceRequirement)
        .implBy({ (wc, minOccurrence) =>
          wc.asScala.toSeq.sortBy(_._2).reverse.takeWhile(_._2 > minOccurrence).map(_._1)
        })
    )
  )

  def datasetTokenForTask(task: DecaTask): Token[DecaTokenizedDataset] = {
    task match {
      case DecaTask.QuestionAnswering =>
        SquadLoader.tokenizedTrainData
    }
  }

}

object DataLoader {
  type WordCount = ConcurrentHashMap[String, Long]

  def wordCounts(decaTokenizedDataset: DecaTokenizedDataset): WordCount = {
    decaTokenizedDataset.records.par
      .foldLeft(new ConcurrentHashMap[String, Long]()) { (cache, record) =>
        for (w <- record.question) {
          cache.compute(w, (_, v) => {
            if (Objects.isNull(v)) {
              1
            } else {
              v + 1
            }
          })
        }
        cache
      }
  }
}
