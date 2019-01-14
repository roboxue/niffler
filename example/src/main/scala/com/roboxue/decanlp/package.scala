package com.roboxue
import java.io.{File, PrintWriter}
import java.net.URI

import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods.compact

package object decanlp {
  val TRAIN_DATA_FILENAME = "train.json"
  val TEST_DATA_FILENAME = "test.json"
  val VALIDATION_DATA_FILENAME = "validation.json"

  object DecaTask {

    case object CommonsenseReasoning extends DecaTask
    case object GoalOrientedDialogue extends DecaTask
    case object NamedEntityRecognition extends DecaTask
    case object NaturalLanguageInference extends DecaTask
    case object QuestionAnswering extends DecaTask
    case object RelationExtraction extends DecaTask
    case object SemanticParsing extends DecaTask
    case object SemanticRoleLabeling extends DecaTask
    case object SentimentAnalysis extends DecaTask
    case object Summarization extends DecaTask
    case class MachineTranslation(sourceLanguage: String, targetLanguage: String) extends DecaTask
  }

  sealed trait DecaTask

  type Tensor[A] = Seq[A]
  type Embeddings = Array[Float]
  type Sentence = String
  // Use file system cache path currently before JNI actually got implemented
  type JNIModelReference = String

  case class Word(word: String, embeddings: Embeddings)

  trait DecaRawInput {
    val question: Sentence
    val context: Sentence
    val answer: Sentence

    def toJson: JObject = {
      import org.json4s.JsonDSL._
      ("context" -> context) ~
        ("question" -> question) ~
        ("answer" -> answer)
    }

    def toJsonl: String = {
      compact(toJson)
    }

    def tokenize(tokenizer: Sentence => Seq[String]): DecaTokenizedResult = {
      DecaTokenizedResult(tokenizer(question), tokenizer(context), tokenizer(answer))
    }
  }

  case class QuestionAnswerContext(question: Sentence, answer: Sentence, context: Sentence) extends DecaRawInput

  case class DecaTokenizedResult(question: Seq[String], context: Seq[String], answer: Seq[String])

  case class DecaDataset(records: Seq[DecaRawInput]) {
    def writeTo(file: File): Unit = {
      val writer = new PrintWriter(file)
      try {
        records.foreach(r => {
          writer.println(compact(r.toJson))
        })
      } finally {
        writer.close()
      }
    }
  }

  case class DecaTokenizedDataset(records: Seq[DecaTokenizedResult]) {
    def batchIterator: Iterator[MiniBatch] = {
      ???
    }
  }

  case class DecaIndexedInput(questionIndexes: Seq[Int], contextIndexes: Seq[Int], answerIndexes: Seq[Int])
  case class MiniBatch(data: Seq[DecaTokenizedResult], vocabulary: Seq[Word])
  case class ModelSnapshot(model: JNIModelReference, loss: Float)
  case class DownloadInstruction(uri: URI, fileName: String)
}
