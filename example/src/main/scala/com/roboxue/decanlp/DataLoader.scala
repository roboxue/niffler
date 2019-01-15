package com.roboxue.decanlp
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import com.roboxue.decanlp.DataLoader._
import com.roboxue.niffler._
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, _}

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
      case DecaTask.NamedEntityRecognition =>
      // Not supported
      case DecaTask.NaturalLanguageInference =>
        _dataFlows ++= NaturalLanguageInference.dataFlows
        _dataFlows ++= Seq(
          NaturalLanguageInference.workingDirectory
            .dependsOn(workingDirectory)
            .implBy(_.resolve("natural_language_inference")),
          prepareAllData ++= NaturalLanguageInference.prepareAllData
        )
      case DecaTask.QuestionAnswering =>
        _dataFlows ++= QuestionAnswering.dataFlows
        _dataFlows ++= Seq(
          QuestionAnswering.workingDirectory.dependsOn(workingDirectory).implBy(_.resolve("question_answering")),
          prepareAllData ++= QuestionAnswering.prepareAllData
        )
      case DecaTask.SemanticParsing =>
        _dataFlows ++= SemanticParsing.dataFlows
        _dataFlows ++= Seq(
          SemanticParsing.workingDirectory.dependsOn(workingDirectory).implBy(_.resolve("semantic_parsing")),
          prepareAllData ++= SemanticParsing.prepareAllData
        )
      case DecaTask.SemanticRoleLabeling =>
        _dataFlows ++= SemanticRoleLabeling.dataFlows
        _dataFlows ++= Seq(
          SemanticRoleLabeling.workingDirectory.dependsOn(workingDirectory).implBy(_.resolve("semantic_role_labeling")),
          prepareAllData ++= SemanticRoleLabeling.prepareAllData
        )
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

  protected trait BaseDataLoader extends Niffler {
    private val clazzName = getClass.getSimpleName.stripSuffix("$")
    val workingDirectory: Token[Path] = Token(s"the working directory for data loading and splitting for $clazzName")
    val trainJsonl: Token[File] = Token(s"training data for $clazzName")
    val validationJsonl: Token[File] = Token(s"validation data $clazzName")
    val testJsonl: Token[File] = Token(s"test data for $clazzName")
    val prepareAllData: Token[Seq[File]] = Token(s"ready to use dataset for $clazzName")
    def extraDataFlows: Seq[DataFlow[_]]

    override final def dataFlows: Seq[DataFlow[_]] =
      Seq(prepareAllData.dependsOnAllOf(trainJsonl, testJsonl, validationJsonl).implBy(files => files)) ++ extraDataFlows
  }

  object CommonsenseReasoning extends BaseDataLoader {
    val parsedWinogradSchema: Token[Seq[QuestionAnswerContext]] = Token(
      "parse winograd schema into question context and answer"
    )

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
    )
  }

  object GoalOrientedDialogue extends BaseDataLoader {
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
      )
  }

  object NaturalLanguageInference extends BaseDataLoader {
    private[decanlp] def parseSNLI(file: File): Seq[QuestionAnswerContext] = {
      val examples = ListBuffer.empty[QuestionAnswerContext]
      Source
        .fromFile(file)
        .getLines()
        .foreach(line => {
          for (JObject(root) <- parse(line);
               JField("sentence1", JString(sentence1)) <- root;
               JField("sentence2", JString(sentence2)) <- root;
               JField("gold_label", JString(goldLabel)) <- root) {
            examples += QuestionAnswerContext(
              s"""Hypothesis: "$sentence2" -- entailment, neutral, or contradiction?""",
              goldLabel,
              s"""Premise: "$sentence1""""
            )
          }
        })
      examples
    }

    private[decanlp] def parseMultiNLI(file: File): Seq[QuestionAnswerContext] = parseSNLI(file)

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      trainJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.NaturalLanguageInference.snliData,
          DataDownload.NaturalLanguageInference.multiNLIData
        )
        .implBy((dir, snli, multinli) => {
          val examples = parseSNLI(snli.toPath.resolve("snli_1.0_train.jsonl").toFile) ++ parseMultiNLI(
            multinli.toPath.resolve("multinli_1.0_train.jsonl").toFile
          )
          Utils.writeToFile(dir.resolve("train.jsonl").toFile, writer => {
            examples.foreach(l => writer.println(l.toJsonl))
          })
        }),
      validationJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.NaturalLanguageInference.snliData,
          DataDownload.NaturalLanguageInference.multiNLIData
        )
        .implBy((dir, snli, multinli) => {
          val examples = parseSNLI(snli.toPath.resolve("snli_1.0_dev.jsonl").toFile) ++
            parseMultiNLI(multinli.toPath.resolve("multinli_1.0_dev_matched.jsonl").toFile) ++
            parseMultiNLI(multinli.toPath.resolve("multinli_1.0_dev_mismatched.jsonl").toFile)
          Utils.writeToFile(dir.resolve("validation.jsonl").toFile, writer => {
            examples.foreach(l => writer.println(l.toJsonl))
          })
        }),
      testJsonl
        .dependsOn(workingDirectory, DataDownload.NaturalLanguageInference.snliData)
        .implBy((dir, snli) => {
          val examples = parseSNLI(snli.toPath.resolve("snli_1.0_test.jsonl").toFile)
          Utils.writeToFile(dir.resolve("test.jsonl").toFile, writer => {
            examples.foreach(l => writer.println(l.toJsonl))
          })
        }),
    )
  }

  object QuestionAnswering extends BaseDataLoader {
    case class SquadParagraph(context: String, qas: Seq[SquadQA])
    case class SquadQA(question: String, id: String, answers: Seq[SquadAnswer])
    case class SquadAnswer(text: String, answer_start: Int)

    private[decanlp] def parseZeroShot(zreFile: File): Seq[QuestionAnswerContext] = {
      Source
        .fromFile(zreFile)
        .getLines()
        .map(line => {
          val elements = line.split('\t')
          if (elements.length == 4) {
            val Array(_, question, subject, context) = elements
            QuestionAnswerContext(question.replace("XXX", subject), "unanswerable", context)
          } else {
            val Array(_, question, subject, context) = elements.take(4)
            val answer = elements.drop(4).mkString(", ")
            QuestionAnswerContext(question.replace("XXX", subject), answer, context)
          }
        })
        .toSeq
    }

    private[decanlp] def parseSquad(file: File): Seq[QuestionAnswerContext] = {
      implicit val format: Formats = org.json4s.DefaultFormats
      for (JArray(paragraphs) <- parse(file) \ "data" \ "paragraphs";
           JObject(p) <- paragraphs;
           JField("context", JString(context)) <- p;
           JField("qas", JArray(qas)) <- p;
           JObject(qa) <- qas;
           JField("question", JString(question)) <- qa;
           JField("answers", JArray(answers)) <- qa) yield {
        if (answers.isEmpty) {
          QuestionAnswerContext(question, "unanswerable", context)
        } else {
          val answer = (answers.head \ "text").extract[String]
          QuestionAnswerContext(question, answer, context)
        }
      }
    }

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      trainJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.QuestionAnswering.squadTrainJson,
          DataDownload.QuestionAnswering.zeroShotREData
        )
        .implBy((dir, squad, zre) => {
          Utils.writeToFile(
            dir.resolve("train.jsonl").toFile,
            writer => {
              Range(0, 5)
                .flatMap(i => parseZeroShot(zre.toPath.resolve(s"train.$i").toFile))
                .foreach(l => writer.println(l.toJsonl))
              parseSquad(squad).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
      validationJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.QuestionAnswering.squadDevJson,
          DataDownload.QuestionAnswering.zeroShotREData
        )
        .implBy((dir, squad, zre) => {
          Utils.writeToFile(
            dir.resolve("validation.jsonl").toFile,
            writer => {
              Range(0, 5)
                .flatMap(i => parseZeroShot(zre.toPath.resolve(s"dev.$i").toFile))
                .foreach(l => writer.println(l.toJsonl))
              parseSquad(squad).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
      testJsonl
        .dependsOn(workingDirectory, DataDownload.QuestionAnswering.zeroShotREData)
        .implBy((dir, zre) => {
          Utils.writeToFile(
            dir.resolve("test.jsonl").toFile,
            writer => {
              Range(0, 5)
                .flatMap(i => parseZeroShot(zre.toPath.resolve(s"test.$i").toFile))
                .foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
    )
  }

  object SemanticParsing extends BaseDataLoader {
    private[decanlp] def parseWikiSqlData(tableFile: File,
                                          dataFile: File,
                                          useQueryAsQuestion: Boolean): Seq[QuestionAnswerContext] = {
      implicit val formats: Formats = DefaultFormats
      val tables: Map[String, WikiSqlTable] =
        Source
          .fromFile(tableFile)
          .getLines()
          .map(line => {
            val table = parse(line).extract[WikiSqlTable]
            table.id -> table
          })
          .toMap

      Source
        .fromFile(dataFile)
        .getLines()
        .map(l => {
          val entry = parse(l).extract[WikiSqlLine]
          val table = tables(entry.table_id)
          val answer = WikiSqlBuilder.toSqlQuery(entry.sql, table.header)
          val (question, context) = if (useQueryAsQuestion) {
            (
              entry.question,
              s"The table has columns ${table.header.mkString(", ")} and key words " +
                s"${(WikiSqlBuilder.agg_ops.drop(0) ++ WikiSqlBuilder.cond_ops ++ WikiSqlBuilder.sysms).mkString(", ")}"
            )
          } else {
            ("What is the translation from English to SQL?", entry.question)
          }
          QuestionAnswerContext(question, answer, context)
        })
        .toSeq

    }
    case class WikiSqlTable(header: Seq[String], id: String)
    case class WikiSqlLine(table_id: String, question: String, sql: WikiSqlQuery)
    case class WikiSqlQuery(agg: Int, sel: Int, conds: Seq[Seq[String]])
    object WikiSqlBuilder {
      val agg_ops: Seq[JNIModelReference] = Seq("", "MAX", "MIN", "COUNT", "SUM", "AVG")
      val cond_ops: Seq[JNIModelReference] = Seq("=", ">", "<", "OP")
      val sysms: Seq[JNIModelReference] = Seq(
        "SELECT",
        "WHERE",
        "AND",
        "COL",
        "TABLE",
        "CAPTION",
        "PAGE",
        "SECTION",
        "OP",
        "COND",
        "QUESTION",
        "AGG",
        "AGGOPS",
        "CONDOPS"
      )
      def toSqlQuery(sql: WikiSqlQuery, header: Seq[String]): String = {
        val query = StringBuilder.newBuilder
        def getCol(i: Int): String = {
          if (header.isEmpty) s"col$i" else s"`${header(i)}`"
        }
        query.append(s"""SELECT ${agg_ops(sql.agg)} ${getCol(sql.sel)} FROM table""")
        if (sql.conds.nonEmpty) {
          query.append((for (Seq(column, operator, value) <- sql.conds) yield {
            s"${getCol(column.toInt)} ${cond_ops(operator.toInt)} '$value'"
          }).mkString(" WHERE ", " AND ", ""))
        }
        query.toString()
      }
    }

    val useQueryAsQuestion: Token[Boolean] = Token("use query as question if true, otherwise as context")

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      useQueryAsQuestion := false,
      trainJsonl
        .dependsOn(workingDirectory, useQueryAsQuestion, DataDownload.SemanticParsing.wikiSqlData)
        .implBy((dir, useQueryAsQuestion, wikiSql) => {
          Utils.writeToFile(
            dir.resolve("train.jsonl").toFile,
            writer => {
              parseWikiSqlData(
                wikiSql.toPath.resolve("train.tables.jsonl").toFile,
                wikiSql.toPath.resolve("train.jsonl").toFile,
                useQueryAsQuestion
              ).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
      validationJsonl
        .dependsOn(workingDirectory, useQueryAsQuestion, DataDownload.SemanticParsing.wikiSqlData)
        .implBy((dir, useQueryAsQuestion, wikiSql) => {
          Utils.writeToFile(
            dir.resolve("validation.jsonl").toFile,
            writer => {
              parseWikiSqlData(
                wikiSql.toPath.resolve("dev.tables.jsonl").toFile,
                wikiSql.toPath.resolve("dev.jsonl").toFile,
                useQueryAsQuestion
              ).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
      testJsonl
        .dependsOn(workingDirectory, useQueryAsQuestion, DataDownload.SemanticParsing.wikiSqlData)
        .implBy((dir, useQueryAsQuestion, wikiSql) => {
          Utils.writeToFile(
            dir.resolve("test.jsonl").toFile,
            writer => {
              parseWikiSqlData(
                wikiSql.toPath.resolve("test.tables.jsonl").toFile,
                wikiSql.toPath.resolve("test.jsonl").toFile,
                useQueryAsQuestion
              ).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
    )
  }

  object SemanticRoleLabeling extends BaseDataLoader {
    private[decanlp] def clean(line: String): String = {
      line
        .replaceAll(" ([\\.,;!?:}%]|'ll|n't|'s|'m|'d|'re)", "$1")
        .replaceAll("([\\($]) ", "$1")
        .replaceAll(" ([-]) ", "$1")
    }

    private[decanlp] def parseQASRL(file: File): Seq[QuestionAnswerContext] = {
      val examples = ListBuffer.empty[QuestionAnswerContext]
      var context: Option[String] = None
      Source
        .fromFile(file)
        .getLines()
        .foreach(line => {
          if (line.isEmpty) {
            context = None
          } else if (line.startsWith("WIKI1")) {
            // ignore head line
          } else if (context.isEmpty) {
            context = Some(clean(line))
          } else if (line.matches("^\\d+\\t\\D+\\t\\d+$")) {
            // ignore token line
          } else {
            val Array(rawQuestion, rawAnswers) = line.split("\t\\?\t").take(2)
            val question = clean(rawQuestion.replaceAll("_", "").replaceAll("\\s+", " ")).replaceAll("\\s$", "?")
            val answer = rawAnswers.split("###").head
            examples += QuestionAnswerContext(question, answer, context.get)
          }
        })
      examples
    }

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      trainJsonl
        .dependsOn(workingDirectory, DataDownload.SemanticRoleLabeling.qasrlTrainData)
        .implBy((workingDirectory, qasrl) => {
          Utils.writeToFile(workingDirectory.resolve("train.jsonl").toFile, writer => {
            parseQASRL(qasrl).foreach(l => writer.println(l.toJsonl))
          })
        }),
      validationJsonl
        .dependsOn(workingDirectory, DataDownload.SemanticRoleLabeling.qasrlDevData)
        .implBy((workingDirectory, qasrl) => {
          Utils.writeToFile(workingDirectory.resolve("validation.jsonl").toFile, writer => {
            parseQASRL(qasrl).foreach(l => writer.println(l.toJsonl))
          })
        }),
      testJsonl
        .dependsOn(workingDirectory, DataDownload.SemanticRoleLabeling.qasrlTestData)
        .implBy((workingDirectory, qasrl) => {
          Utils.writeToFile(workingDirectory.resolve("test.jsonl").toFile, writer => {
            parseQASRL(qasrl).foreach(l => writer.println(l.toJsonl))
          })
        }),
    )
  }
}
