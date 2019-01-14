package com.roboxue.decanlp
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import com.roboxue.decanlp.DataLoader._
import com.roboxue.niffler._
import org.json4s.{DefaultFormats, _}
import org.json4s.jackson.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.Source

class DataLoader(decaTasks: Seq[DecaTask]) extends Niffler {
  val minOccurrenceRequirement: Token[Int] = Token(
    "the minimum amount of occurrence required to be counted as a vocabulary"
  )
  val wordCounts: Token[WordCount] = Token("the word count stats for all data")
  val sharedVocabulary: Token[Seq[String]] = Token("list of words sorted by their frequency desc")

  override def dataFlows: Iterable[DataFlow[_]] = {
    val _dataFlows = ListBuffer[DataFlow[_]]()
    val dynamicTokens = ListBuffer[Token[DataLoader.WordCount]]()

    decaTasks.foreach({
      case DecaTask.CommonsenseReasoning =>
        _dataFlows ++= CommonsenseReasoning.dataFlows
        _dataFlows ++= Seq(
          CommonsenseReasoning.workingDirectory.dependsOn(workingDirectory).implBy(_.resolve("commonsense_reasoning")),
          prepareAllData ++= CommonsenseReasoning.prepareAllData
        )
      case DecaTask.GoalOrientedDialogue =>
        _dataFlows ++= GoalOrientedDialogue.dataFlows
        _dataFlows ++= Seq(
          GoalOrientedDialogue.workingDirectory.dependsOn(workingDirectory).implBy(_.resolve("goal_oriented_dialogue")),
          prepareAllData ++= GoalOrientedDialogue.prepareAllData
        )
      case DecaTask.NamedEntityRecognition                                             =>
      case DecaTask.NaturalLanguageInference                                           =>
      case DecaTask.QuestionAnswering                                                  =>
      case DecaTask.RelationExtraction                                                 =>
      case DecaTask.SemanticParsing                                                    =>
      case DecaTask.SemanticRoleLabeling                                               =>
      case DecaTask.SentimentAnalysis                                                  =>
      case DecaTask.Summarization                                                      =>
      case DecaTask.MachineTranslation(sourceLanguage: String, targetLanguage: String) =>
    })

    _dataFlows ++= Seq(
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
    _dataFlows
  }
}

object DataLoader {
  type WordCount = ConcurrentHashMap[String, Long]

  val prepareAllData: AccumulatorToken[Seq[File]] =
    Token.accumulator("ready to use dataset for this deep learning task")
  val workingDirectory: Token[Path] = Token("the working directory for data loading and splitting")

//  def wordCounts(decaTokenizedDataset: DecaTokenizedDataset): WordCount = {
//    decaTokenizedDataset.records.par
//      .foldLeft(new ConcurrentHashMap[String, Long]()) { (cache, record) =>
//        for (w <- record.question) {
//          cache.compute(w, (_, v) => {
//            if (Objects.isNull(v)) {
//              1
//            } else {
//              v + 1
//            }
//          })
//        }
//        cache
//      }
//  }

  trait BaseDataLoader extends Niffler {
    private val clazzName = getClass.getSimpleName.stripSuffix("$")
    val workingDirectory: Token[Path] = Token(s"the working directory for data loading and splitting for $clazzName")
    val prepareAllData: Token[Seq[File]] = Token(s"ready to use dataset for $clazzName")
    def extraDataFlows: Seq[DataFlow[_]]

    override final def dataFlows: Seq[DataFlow[_]] = extraDataFlows
  }

  object CommonsenseReasoning extends BaseDataLoader {
    val parsedWinogradSchema: Token[Seq[QuestionAnswerContext]] = Token(
      "parse winograd schema into question context and answer"
    )
    val trainJsonl: Token[File] = Token("training data")
    val validationJsonl: Token[File] = Token("validation data")
    val testJsonl: Token[File] = Token("test data")

    private[decanlp] def extractVariations(context: String): Seq[String] = {
      val regex = "\\[.*\\]".r
      regex.findAllMatchIn(context).toSeq.headOption match {
        case Some(m) =>
          for (variation <- m.matched.stripPrefix("[").stripSuffix("]").split('/')) yield {
            s"${m.before}$variation${m.after}"
          }
        case None =>
          Seq(context, context)
      }

    }

    private[decanlp] def parseSchema(schemaCache: ListBuffer[String]): Seq[QuestionAnswerContext] = {
      val List(context, question, answer) = schemaCache.toList
      val contexts = extractVariations(context)
      val questions = extractVariations(question)
      val answers = answer.split('/')
      for (i <- Range(0, 2)) yield {
        QuestionAnswerContext(questions(i), answers(i), contexts(i))
      }
    }

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      parsedWinogradSchema
        .dependsOn(DataDownload.CommonsenseReasoning.winogradSchemaData)
        .implBy(winograd => {
          val schema = ListBuffer[String]()
          val examples = ListBuffer[QuestionAnswerContext]()
          for (line <- Source.fromFile(winograd).getLines()) {
            if (line.isEmpty) {
              examples ++= parseSchema(schema)
              schema.clear()
            } else {
              schema += line.trim
            }
          }
          examples ++= parseSchema(schema)
          examples
        }),
      trainJsonl
        .dependsOn(workingDirectory, parsedWinogradSchema)
        .implBy((dir, winogradExamples) => {
          Utils.writeToFile(dir.resolve("train.jsonl").toFile, writer => {
            winogradExamples.take(80).foreach(l => writer.println(l.toJsonl))
          })
        }),
      testJsonl
        .dependsOn(workingDirectory, parsedWinogradSchema)
        .implBy((dir, winogradExamples) => {
          Utils.writeToFile(dir.resolve("test.jsonl").toFile, writer => {
            winogradExamples.takeRight(100).foreach(l => writer.println(l.toJsonl))
          })
        }),
      validationJsonl
        .dependsOn(workingDirectory, parsedWinogradSchema)
        .implBy((dir, winogradExamples) => {
          Utils.writeToFile(dir.resolve("validation.jsonl").toFile, writer => {
            winogradExamples.drop(80).dropRight(100).foreach(l => writer.println(l.toJsonl))
          })
        }),
      prepareAllData.dependsOnAllOf(trainJsonl, testJsonl, validationJsonl).implBy(files => files)
    )
  }

  object GoalOrientedDialogue extends BaseDataLoader {
    val trainJsonl: Token[File] = Token("training data")
    val validationJsonl: Token[File] = Token("validation data")
    val testJsonl: Token[File] = Token("test data")

    private[decanlp] def parseWozDialogues(dialogues: JArray): Seq[QuestionAnswerContext] = {
      implicit val formats: Formats = DefaultFormats
      val examples = ListBuffer.empty[QuestionAnswerContext]
      dialogues.arr.foreach(d => {
        var previousInformation = Map.empty[String, String]
        var previousRequest = List.empty[String]
        val turns = (d \ "dialogue").extract[JArray]
        turns.arr.foreach(t => {
          val question = "What is the change in state?"
          val actions = (t \ "system_acts")
            .extract[JArray]
            .arr
            .map({
              case JArray(act) =>
                act.map(_.extract[String]).mkString(": ")
              case JString(act) =>
                act
            })
            .mkString(", ")
          val context = if (actions.isEmpty) {
            (t \ "transcript").extract[String]
          } else {
            s"$actions -- ${(t \ "transcript").extract[String]}"
          }
          val deltaInformation = mutable.Map.empty[String, String]
          val deltaRequest = ListBuffer.empty[String]
          val currentInformation = mutable.Map.empty[String, String]
          val currentRequest = ListBuffer.empty[String]
          for (JObject(item) <- t \ "belief_state";
               JField("slots", JArray(slots)) <- item;
               JField("act", JString(act)) <- item;
               slot <- slots) {
            act match {
              case "inform" =>
                val List(informationKey, informationValue) = slot.extract[List[String]]
                currentInformation(informationKey) = informationValue
                if (previousInformation.get(informationKey).contains(informationValue)) {
                  // redudant information
                } else {
                  deltaInformation(informationKey) = informationValue
                }
              case "request" =>
                val List(_, requestValue) = slot.extract[List[String]]
                deltaRequest += requestValue
                currentRequest += requestValue
            }
          }
          previousInformation = currentInformation.toMap
          previousRequest = currentRequest.toList
          val newInformation = deltaInformation
            .map({
              case (key, value) => s"$key: $value"
            })
            .mkString(", ")
          val newRequest = deltaRequest.mkString(", ")
          val answer = if (newRequest.nonEmpty || newInformation.nonEmpty) {
            Seq(newInformation, newRequest).mkString("; ").trim
          } else {
            "None"
          }
          examples += QuestionAnswerContext(question, answer, context)
        })
      })
      examples
    }

    override def extraDataFlows: Seq[DataFlow[_]] =
      Seq(
        trainJsonl
          .dependsOn(
            workingDirectory,
            DataDownload.GoalOrientedDialogue.wozTrainDataEn,
            DataDownload.GoalOrientedDialogue.wozTrainDataDe
          )
          .implBy((dir, enFile, deFile) => {
            implicit val formats: Formats = DefaultFormats
            val examples = parseWozDialogues(parse(enFile).extract[JArray]) ++ parseWozDialogues(
              parse(deFile).extract[JArray]
            )
            Utils.writeToFile(dir.resolve("train.jsonl").toFile, writer => {
              examples.foreach(l => writer.println(l.toJsonl))
            })
          }),
        validationJsonl
          .dependsOn(
            workingDirectory,
            DataDownload.GoalOrientedDialogue.wozValidationDataEn,
            DataDownload.GoalOrientedDialogue.wozValidationDataDe
          )
          .implBy((dir, enFile, deFile) => {
            implicit val formats: Formats = DefaultFormats
            val examples = parseWozDialogues(parse(enFile).extract[JArray]) ++ parseWozDialogues(
              parse(deFile).extract[JArray]
            )
            Utils.writeToFile(dir.resolve("validation.jsonl").toFile, writer => {
              examples.foreach(l => writer.println(l.toJsonl))
            })
          }),
        testJsonl
          .dependsOn(
            workingDirectory,
            DataDownload.GoalOrientedDialogue.wozTestDataEn,
            DataDownload.GoalOrientedDialogue.wozTestDataDe
          )
          .implBy((dir, enFile, deFile) => {
            implicit val formats: Formats = DefaultFormats
            val examples = parseWozDialogues(parse(enFile).extract[JArray]) ++ parseWozDialogues(
              parse(deFile).extract[JArray]
            )
            Utils.writeToFile(dir.resolve("test.jsonl").toFile, writer => {
              examples.foreach(l => writer.println(l.toJsonl))
            })
          }),
        prepareAllData.dependsOnAllOf(testJsonl, trainJsonl, validationJsonl).implBy(files => files),
      )
  }
}
