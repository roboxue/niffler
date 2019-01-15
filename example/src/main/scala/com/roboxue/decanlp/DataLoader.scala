package com.roboxue.decanlp
import java.io.{File, FileInputStream}
import java.nio.file.Path
import java.text.Normalizer
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque}

import com.roboxue.decanlp.DataLoader._
import com.roboxue.niffler._
import org.apache.commons.codec.digest.DigestUtils
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, _}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.xml.XML

class DataLoader(decaTasks: Seq[DecaTask]) extends Niffler {
  val minOccurrenceRequirement: Token[Int] = Token(
    "the minimum amount of occurrence required to be counted as a vocabulary"
  )
  val wordCounts: Token[WordCount] = Token("the word count stats for all data")
  val sharedVocabulary: Token[Seq[String]] = Token("list of words sorted by their frequency desc")

  override def dataFlows: Iterable[DataFlow[_]] = {
    val _dataFlows = ListBuffer[DataFlow[_]]()
    val dynamicTokens = ListBuffer[Token[DataLoader.WordCount]]()

    def addDataLoader(loader: BaseDataLoader, relativePath: String): Unit = {
      _dataFlows ++= loader.dataFlows
      _dataFlows ++= Seq(
        loader.workingDirectory.dependsOn(workingDirectory).implBy(_.resolve(relativePath)),
        prepareAllData ++= loader.prepareAllData
      )
    }

    decaTasks.foreach({
      case DecaTask.CommonsenseReasoning =>
        addDataLoader(CommonsenseReasoning, "commonsense_reasoning")
      case DecaTask.GoalOrientedDialogue =>
        addDataLoader(GoalOrientedDialogue, "goal_oriented_dialogue")
      case DecaTask.NamedEntityRecognition =>
      // Not supported
      case DecaTask.NaturalLanguageInference =>
        addDataLoader(NaturalLanguageInference, "natural_language_inference")
      case DecaTask.QuestionAnswering =>
        addDataLoader(QuestionAnswering, "question_answering")
      case DecaTask.SemanticParsing =>
        addDataLoader(SemanticParsing, "semantic_parsing")
      case DecaTask.SemanticRoleLabeling =>
        addDataLoader(SemanticRoleLabeling, "semantic_role_labeling")
      case DecaTask.SentimentAnalysis =>
        addDataLoader(SentimentAnalysis, "sentiment_analysis")
      case DecaTask.Summarization =>
        addDataLoader(Summarization, "summarization")
      case t: DecaTask.MachineTranslation =>
        addDataLoader(MachineTranslation, "machine_translation")
        _dataFlows ++= Seq(MachineTranslation.machineTranslationTasks += t)
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

  object SentimentAnalysis extends BaseDataLoader {
    private[decanlp] val SENTIMENT_QUESTION: String = "Is this review negative or positive?".intern()
    private[decanlp] val SENTIMENT_NEGATIVE: String = "negative".intern()
    private[decanlp] val SENTIMENT_POSITIVE: String = "positive".intern()

    private[decanlp] def parseImdb(folderPath: Path): Seq[QuestionAnswerContext] = {
      val examples = ListBuffer.empty[QuestionAnswerContext]
      for ((subFolder, answer) <- Seq("neg" -> SENTIMENT_NEGATIVE, "pos" -> SENTIMENT_POSITIVE)) {
        for (f <- folderPath.resolve(subFolder).toFile.listFiles(_.getName.endsWith(".txt"))) {
          val context = Source.fromFile(f).getLines().next().replaceAll("<br />", " ")
          examples += QuestionAnswerContext(SENTIMENT_QUESTION, answer, context)
        }
      }
      examples
    }

    private[decanlp] def parseTreeBank(file: File): Seq[QuestionAnswerContext] = {
      Source
        .fromFile(file)
        .getLines()
        .drop(1)
        .map(line => {
          val (flag, rawContext) = line.splitAt(1)
          val answer = flag match {
            case "0" => SENTIMENT_NEGATIVE
            case "1" => SENTIMENT_POSITIVE
          }
          val context = rawContext.drop(1).replaceAll("^\"", "").replaceAll("\"$", "")
          QuestionAnswerContext(SENTIMENT_QUESTION, answer, context)
        })
        .toSeq
    }

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      trainJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.SentimentAnalysis.imdbReviewData,
          DataDownload.SentimentAnalysis.treeBankSubsetTrainData
        )
        .implBy((dir, imdb, treeBank) => {
          Utils.writeToFile(
            dir.resolve("train.jsonl").toFile,
            writer => {
              parseImdb(imdb.toPath.resolve("train")).foreach(l => writer.println(l.toJsonl))
              parseTreeBank(treeBank).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
      validationJsonl
        .dependsOn(workingDirectory, DataDownload.SentimentAnalysis.treeBankSubsetDevData)
        .implBy((dir, treeBank) => {
          Utils.writeToFile(dir.resolve("validation.jsonl").toFile, writer => {
            parseTreeBank(treeBank).foreach(l => writer.println(l.toJsonl))
          })
        }),
      testJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.SentimentAnalysis.imdbReviewData,
          DataDownload.SentimentAnalysis.treeBankSubsetTrainData
        )
        .implBy((dir, imdb, treeBank) => {
          Utils.writeToFile(
            dir.resolve("test.jsonl").toFile,
            writer => {
              parseImdb(imdb.toPath.resolve("test")).foreach(l => writer.println(l.toJsonl))
              parseTreeBank(treeBank).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
    )
  }

  object Summarization extends BaseDataLoader {
    private[decanlp] val endTokens: Set[String] = Set(".", "!", "?", "...", "'", "`", "\"", "\u2019", "\u201d", ")")
    private[decanlp] val SUMMARIZATION_QUESTION = "What is the summary?".intern()
    private[decanlp] def addPeriod(line: String): String = {
      if (line.matches("^.*([\\.!?'`\"\u2019\u201d\\)]|\\.\\.\\.)$")) line else line + "."
    }

    private[decanlp] def parseStory(storiesDir: Path, storyIdFile: File): Seq[QuestionAnswerContext] = {
      val examples = new ConcurrentLinkedDeque[QuestionAnswerContext]
      Source
        .fromFile(storyIdFile)
        .getLines()
        .toSeq
        .par
        .foreach(url => {
          val sha = DigestUtils.sha1Hex(url.trim())
          val storyFile = storiesDir.resolve(s"$sha.story").toFile
          if (storyFile.exists()) {
            val context = ListBuffer.empty[String]
            val answer = ListBuffer.empty[String]
            var highlightStarted = false
            Source
              .fromFile(storyFile)
              .getLines()
              .foreach(line => {
                if (line.trim == "@highlight") {
                  highlightStarted = true
                } else if (line.trim.nonEmpty) {
                  if (highlightStarted) {
                    answer += addPeriod(line.trim)
                  } else {
                    context += addPeriod(line.trim)
                  }
                }
              })

            examples.add(
              QuestionAnswerContext(
                SUMMARIZATION_QUESTION,
                Normalizer.normalize(answer.mkString(" "), Normalizer.Form.NFKC),
                Normalizer.normalize(context.mkString(" "), Normalizer.Form.NFKC)
              )
            )
          } else {
            // log it
          }
        })
      examples.asScala.toSeq
    }

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      trainJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.Summarization.cnnStoriesData,
          DataDownload.Summarization.cnnTrainId,
          DataDownload.Summarization.dailyMailStoriesData,
          DataDownload.Summarization.dailyMailTrainId
        )
        .implBy((dir, cnn, cnnId, dailyMail, dailyMailId) => {
          Utils.writeToFile(
            dir.resolve("train.jsonl").toFile,
            writer => {
              parseStory(cnn.toPath.resolve("stories"), cnnId).foreach(l => writer.println(l.toJsonl))
              parseStory(dailyMail.toPath.resolve("stories"), dailyMailId).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
      validationJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.Summarization.cnnStoriesData,
          DataDownload.Summarization.cnnValidationId,
          DataDownload.Summarization.dailyMailStoriesData,
          DataDownload.Summarization.dailyMailValidationId
        )
        .implBy((dir, cnn, cnnId, dailyMail, dailyMailId) => {
          Utils.writeToFile(
            dir.resolve("validation.jsonl").toFile,
            writer => {
              parseStory(cnn.toPath.resolve("stories"), cnnId).foreach(l => writer.println(l.toJsonl))
              parseStory(dailyMail.toPath.resolve("stories"), dailyMailId).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
      testJsonl
        .dependsOn(
          workingDirectory,
          DataDownload.Summarization.cnnStoriesData,
          DataDownload.Summarization.cnnTestId,
          DataDownload.Summarization.dailyMailStoriesData,
          DataDownload.Summarization.dailyMailTestId
        )
        .implBy((dir, cnn, cnnId, dailyMail, dailyMailId) => {
          Utils.writeToFile(
            dir.resolve("test.jsonl").toFile,
            writer => {
              parseStory(cnn.toPath.resolve("stories"), cnnId).foreach(l => writer.println(l.toJsonl))
              parseStory(dailyMail.toPath.resolve("stories"), dailyMailId).foreach(l => writer.println(l.toJsonl))
            }
          )
        }),
    )
  }

  object MachineTranslation extends BaseDataLoader {
    val machineTranslationTasks: AccumulatorToken[Seq[DecaTask.MachineTranslation]] =
      Token.accumulator("the machine translation language pairs")

    def parseIwsltTagFile(sourceFile: File,
                          targetFile: File,
                          task: DecaTask.MachineTranslation): Seq[QuestionAnswerContext] = {
      val examples = ListBuffer.empty[QuestionAnswerContext]
      val question = s"Translate from ${task.sourceLanguage} to ${task.targetLanguage}"
      Source
        .fromFile(sourceFile)
        .getLines()
        .zip(Source.fromFile(targetFile).getLines())
        .foreach({
          case (sourceLine, targetLine) =>
            if (sourceLine.matches("^(<url|<keywords|<talkid|<description|<reviewer|<translator|<title|<speaker).*$")) {
              // skip
            } else {
              examples += QuestionAnswerContext(question, targetLine, sourceLine)
            }
        })
      examples
    }

    def parseIwsltXmlFile(sourceFile: File,
                          targetFile: File,
                          task: DecaTask.MachineTranslation): Seq[QuestionAnswerContext] = {
      val examples = ListBuffer.empty[QuestionAnswerContext]
      val question = s"Translate from ${task.sourceLanguage} to ${task.targetLanguage}"
      val source: Seq[String] =
        for (seg <- XML.load(new FileInputStream(sourceFile)) \ "srcset" \ "doc" \ "seg") yield {
          seg.text.trim
        }
      val target =
        for (seg <- XML.load(new FileInputStream(targetFile)) \ "refset" \ "doc" \ "seg") yield {
          seg.text.trim
        }
      source
        .zip(target)
        .foreach({
          case (sourceLine, targetLine) =>
            if (sourceLine.matches("^(<url|<keywords|<talkid|<description|<reviewer|<translator|<title|<speaker)")) {
              // skip
            } else {
              examples += QuestionAnswerContext(question, targetLine, sourceLine)
            }
        })
      examples
    }

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      trainJsonl
        .dependsOn(workingDirectory, DataDownload.MachineTranslation.iwsltData, machineTranslationTasks)
        .implBy((dir, iwsltData, tasks) => {
          Utils.writeToFile(dir.resolve(s"train.jsonl").toFile, writer => {
            for (t @ DecaTask.MachineTranslation(source, _, target, _) <- tasks) {
              parseIwsltTagFile(
                iwsltData.toPath.resolve(s"$source-$target").resolve(s"train.tags.$source-$target.$source").toFile,
                iwsltData.toPath.resolve(s"$source-$target").resolve(s"train.tags.$source-$target.$target").toFile,
                t
              ).foreach(l => writer.println(l.toJsonl))
            }
          })
        }),
      validationJsonl
        .dependsOn(workingDirectory, DataDownload.MachineTranslation.iwsltData, machineTranslationTasks)
        .implBy((dir, iwsltData, tasks) => {
          Utils.writeToFile(dir.resolve(s"validation.jsonl").toFile, writer => {
            for (t @ DecaTask.MachineTranslation(source, _, target, _) <- tasks) {
              parseIwsltXmlFile(
                iwsltData.toPath
                  .resolve(s"$source-$target")
                  .resolve(s"IWSLT16.TED.tst2013.$source-$target.$source.xml")
                  .toFile,
                iwsltData.toPath
                  .resolve(s"$source-$target")
                  .resolve(s"IWSLT16.TED.tst2013.$source-$target.$target.xml")
                  .toFile,
                t
              ).foreach(l => writer.println(l.toJsonl))
            }
          })
        }),
      testJsonl
        .dependsOn(workingDirectory, DataDownload.MachineTranslation.iwsltData, machineTranslationTasks)
        .implBy((dir, iwsltData, tasks) => {
          Utils.writeToFile(dir.resolve(s"test.jsonl").toFile, writer => {
            for (t @ DecaTask.MachineTranslation(source, _, target, _) <- tasks) {
              parseIwsltXmlFile(
                iwsltData.toPath
                  .resolve(s"$source-$target")
                  .resolve(s"IWSLT16.TED.tst2014.$source-$target.$source.xml")
                  .toFile,
                iwsltData.toPath
                  .resolve(s"$source-$target")
                  .resolve(s"IWSLT16.TED.tst2014.$source-$target.$target.xml")
                  .toFile,
                t
              ).foreach(l => writer.println(l.toJsonl))
            }
          })
        }),
    )
  }
}
