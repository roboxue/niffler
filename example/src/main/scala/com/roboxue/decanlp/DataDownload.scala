package com.roboxue.decanlp
import java.io.File
import java.nio.file.Path

import com.roboxue.decanlp.DataDownload._
import com.roboxue.niffler._

import scala.collection.mutable.ListBuffer

class DataDownload(decaTasks: Seq[DecaTask]) extends Niffler {

  override def dataFlows: Iterable[DataFlow[_]] = {
    val _dataFlows = ListBuffer[DataFlow[_]]()
    decaTasks.foreach({
      case DecaTask.QuestionAnswering =>
        _dataFlows ++= QuestionAnswering.dataFlows
        _dataFlows ++= Seq(
          QuestionAnswering.dataPath.dependsOn(dataPath).implBy(_.resolve("question_answering")),
          downloadedData ++= QuestionAnswering.downloadData
        )
      case DecaTask.Summarization =>
        _dataFlows ++= Summarization.dataFlows
        _dataFlows ++= Seq(
          Summarization.dataPath.dependsOn(dataPath).implBy(_.resolve("summarization")),
          downloadedData ++= Summarization.downloadData
        )
      case DecaTask.SemanticParsing =>
        _dataFlows ++= SemanticParsing.dataFlows
        _dataFlows ++= Seq(
          SemanticParsing.dataPath.dependsOn(dataPath).implBy(_.resolve("semantic_parsing")),
          downloadedData ++= SemanticParsing.downloadData
        )
      case DecaTask.SemanticRoleLabeling =>
        _dataFlows ++= SemanticRoleLabeling.dataFlows
        _dataFlows ++= Seq(
          SemanticRoleLabeling.dataPath.dependsOn(dataPath).implBy(_.resolve("semantic_role_labeling")),
          downloadedData ++= SemanticRoleLabeling.downloadData
        )
      case DecaTask.CommonsenseReasoning =>
        _dataFlows ++= CommonsenseReasoning.dataFlows
        _dataFlows ++= Seq(
          CommonsenseReasoning.dataPath.dependsOn(dataPath).implBy(_.resolve("commonsense_reasoning")),
          downloadedData ++= CommonsenseReasoning.downloadData
        )
      case DecaTask.GoalOrientedDialogue =>
        _dataFlows ++= GoalOrientedDialogue.dataFlows
        _dataFlows ++= Seq(
          GoalOrientedDialogue.dataPath.dependsOn(dataPath).implBy(_.resolve("goal_oriented_dialogue")),
          downloadedData ++= GoalOrientedDialogue.downloadData
        )
      case DecaTask.NaturalLanguageInference =>
        _dataFlows ++= NaturalLanguageInference.dataFlows
        _dataFlows ++= Seq(
          NaturalLanguageInference.dataPath.dependsOn(dataPath).implBy(_.resolve("natural_language_inference")),
          downloadedData ++= NaturalLanguageInference.downloadData,
        )
      case DecaTask.NamedEntityRecognition =>
        _dataFlows ++= NamedEntityRecognition.dataFlows
        _dataFlows ++= Seq(
          NamedEntityRecognition.dataPath.dependsOn(dataPath).implBy(_.resolve("named_entity_recognition")),
          downloadedData ++= NamedEntityRecognition.downloadData,
        )
      case DecaTask.SentimentAnalysis =>
        _dataFlows ++= SentimentAnalysis.dataFlows
        _dataFlows ++= Seq(
          SentimentAnalysis.dataPath.dependsOn(dataPath).implBy(_.resolve("sentiment_analysis")),
          downloadedData ++= SentimentAnalysis.downloadData,
        )
      case t @ DecaTask.MachineTranslation(source, target) =>
        _dataFlows ++= MachineTranslation.dataFlows
        _dataFlows ++= Seq(
          MachineTranslation.dataPath.dependsOn(dataPath).implBy(_.resolve(s"machine_translation")),
          MachineTranslation.machineTranslationTasks += t,
          downloadedData ++= MachineTranslation.downloadData,
        )
    })
    _dataFlows
  }
}

object DataDownload {
  val downloadedData: AccumulatorToken[Seq[File]] = Token.accumulator("all downloaded datasets")
  val dataPath: Token[Path] = Token("the working folder for dataset download")

  trait DataDownloader extends Niffler {
    private val clazzName = getClass.getSimpleName.stripSuffix("$")
    val dataPath: Token[Path] = Token(s"the working folder for $clazzName dataset", s"dataPath$clazzName")
    val downloadData: Token[Seq[File]] = Token(s"perform download for $clazzName")

    def extraDataFlows: Seq[DataFlow[_]]

    override final def dataFlows: Seq[DataFlow[_]] = extraDataFlows
  }

  object QuestionAnswering extends DataDownloader {

    val squadTrainJson: Token[File] = Token("the location of the train.json of squad v2")
    val squadDevJson: Token[File] = Token("the location of the dev.json of squad v2")
    val zeroShotREData: Token[File] = Token("zero shot relation extraction dataset")

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      squadTrainJson
        .dependsOn(dataPath)
        .implBy(
          Utils
            .downloadOneFile(
              s"https://rajpurkar.github.io/SQuAD-explorer/dataset/train-v2.0.json",
              "squad_v2_qa_train.json"
            )
        ),
      squadDevJson
        .dependsOn(dataPath)
        .implBy(
          Utils
            .downloadOneFile(
              s"https://rajpurkar.github.io/SQuAD-explorer/dataset/dev-v2.0.json",
              "squad_v2_qa_dev.json"
            )
        ),
      zeroShotREData
        .dependsOn(dataPath)
        .implBy(path => {
          val output = path.resolve("zeroshot_re").toFile
          if (output.exists()) {
            // log this
          } else {
            val archiveFile = Utils
              .downloadOneFile("http://nlp.cs.washington.edu/zeroshot/relation_splits.tar.bz2", "zeroshot_re.tar.bz2")(
                path
              )
            CompressUtils.decompressTarBz(archiveFile, path.toFile)
            path.resolve("relation_splits").toFile.renameTo(output)
          }
          output
        }),
      downloadData
        .dependsOnAllOf(squadTrainJson, squadDevJson, zeroShotREData)
        .implBy(files => files)
    )
  }

  object Summarization extends DataDownloader {
    val dailyMailStoriesData: Token[File] = Token("stories from Daily Mail for summarization")
    val cnnStoriesData: Token[File] = Token("stories from CNN for summarization")

    override def extraDataFlows: Seq[DataFlow[_]] =
      Seq(
        dailyMailStoriesData
          .dependsOn(dataPath)
          .implBy(path => {
            val output = path.resolve("dailymail_summarization").toFile
            if (output.exists()) {
              // log this
            } else {
              val archiveFile = path.resolve("dailymail_stories.tgz").toFile
              Utils.downloadFromGoogleDrive(
                "https://drive.google.com/uc?export=download&id=0BwmD_VLjROrfM1BxdkxVaTY2bWs",
                archiveFile
              )
              CompressUtils.decompressTarGz(archiveFile, path.toFile)
              path.resolve("dailymail").toFile.renameTo(output)
            }
            output
          }),
        cnnStoriesData
          .dependsOn(dataPath)
          .implBy(path => {
            val output = path.resolve("cnn_summarization").toFile
            if (output.exists()) {
              // log this
            } else {
              val archiveFile = path.resolve("cnn_stories.tgz").toFile
              Utils.downloadFromGoogleDrive(
                "https://drive.google.com/uc?export=download&id=0BwmD_VLjROrfTHk4NFg2SndKcjQ",
                archiveFile
              )
              CompressUtils.decompressTarGz(archiveFile, path.toFile)
              path.resolve("cnn").toFile.renameTo(output)
            }
            output
          }),
        downloadData.dependsOnAllOf(dailyMailStoriesData, cnnStoriesData).implBy(files => files)
      )
  }

  object SemanticParsing extends DataDownloader {
    val wikiSqlData: Token[File] = Token("Wikipedia SQL dataset")
    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      wikiSqlData
        .dependsOn(dataPath)
        .implBy(path => {
          val output = path.resolve("wikisql_sp").toFile
          if (output.exists()) {
            // log this
          } else {
            val archiveFile = Utils
              .downloadOneFile("https://github.com/salesforce/WikiSQL/raw/master/data.tar.bz2", "wikisql.tar.bz2")(path)
            CompressUtils.decompressTarBz(archiveFile, path.toFile)
            path.resolve("data").toFile.renameTo(output)
          }
          output
        }),
      downloadData.dependsOn(wikiSqlData).implBy(wikiSqlData => Seq(wikiSqlData)),
    )
  }

  object SemanticRoleLabeling extends DataDownloader {
    val trainData: Token[File] = Token("Wikipedia QA-SRL dataset train data")
    val devData: Token[File] = Token("Wikipedia QA-SRL dataset dev data")
    val testData: Token[File] = Token("Wikipedia QA-SRL dataset test data")
    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      trainData
        .dependsOn(dataPath)
        .implBy(
          Utils
            .downloadOneFile("https://dada.cs.washington.edu/qasrl/data/wiki1.train.qa", "qasrl_srl_train.qa")
        ),
      testData
        .dependsOn(dataPath)
        .implBy(Utils.downloadOneFile("https://dada.cs.washington.edu/qasrl/data/wiki1.test.qa", "qasrl_srl_test.qa")),
      devData
        .dependsOn(dataPath)
        .implBy(Utils.downloadOneFile("https://dada.cs.washington.edu/qasrl/data/wiki1.dev.qa", "qasrl_srl_dev.qa")),
      downloadData
        .dependsOn(trainData, devData, testData)
        .implBy((train, dev, test) => {
          Seq(train, dev, test)
        })
    )
  }

  object CommonsenseReasoning extends DataDownloader {
    val winogradSchemaData: Token[File] = Token("winograd schema data for commensense reasoning")
    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      winogradSchemaData
        .dependsOn(dataPath)
        .implBy(
          Utils
            .downloadOneFile("https://s3.amazonaws.com/research.metamind.io/decaNLP/data/schema.txt", "winograd_cr.txt")
        ),
      downloadData.dependsOn(winogradSchemaData).implBy(winogradSchemaData => Seq(winogradSchemaData)),
    )
  }

  object GoalOrientedDialogue extends DataDownloader {
    val wozTrainDataEn: Token[File] = Token("Wizard of Oz restaurant reservation dataset train data in En")
    val wozValidationDataEn: Token[File] = Token("Wizard of Oz restaurant reservation dataset validation data in En")
    val wozTestDataEn: Token[File] = Token("Wizard of Oz restaurant reservation dataset test data in En")
    val wozTrainDataDe: Token[File] = Token("Wizard of Oz restaurant reservation dataset train data in De")
    val wozValidationDataDe: Token[File] = Token("Wizard of Oz restaurant reservation dataset validation data in De")
    val wozTestDataDe: Token[File] = Token("Wizard of Oz restaurant reservation dataset test data in Germany")
    override def extraDataFlows: Seq[DataFlow[_]] =
      Seq(
        wozTrainDataEn
          .dependsOn(dataPath)
          .implBy(
            Utils.downloadOneFile(
              "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_train_en.json",
              "woz_god_train_en.qa"
            )
          ),
        wozValidationDataEn
          .dependsOn(dataPath)
          .implBy(
            Utils.downloadOneFile(
              "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_validate_en.json",
              "woz_god_validate_en.qa"
            )
          ),
        wozTestDataEn
          .dependsOn(dataPath)
          .implBy(
            Utils.downloadOneFile(
              "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_test_en.json",
              "woz_god_test_en.qa"
            )
          ),
        wozTrainDataDe
          .dependsOn(dataPath)
          .implBy(
            Utils.downloadOneFile(
              "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_train_de.json",
              "woz_god_train_de.qa"
            )
          ),
        wozValidationDataDe
          .dependsOn(dataPath)
          .implBy(
            Utils.downloadOneFile(
              "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_validate_de.json",
              "woz_god_validate_de.qa"
            )
          ),
        wozTestDataDe
          .dependsOn(dataPath)
          .implBy(
            Utils.downloadOneFile(
              "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_test_de.json",
              "woz_god_test_de.qa"
            )
          ),
        downloadData
          .dependsOnAllOf(
            wozTrainDataEn,
            wozValidationDataEn,
            wozTestDataEn,
            wozTrainDataDe,
            wozValidationDataDe,
            wozTestDataDe
          )
          .implBy(files => files)
      )
  }

  object NaturalLanguageInference extends DataDownloader {
    val multiNLIData: Token[File] = Token("multi nli 1.0 dataset")
    val snliData: Token[File] = Token("stanford nli 1.0 dataset")
    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      multiNLIData
        .dependsOn(dataPath)
        .implBy(path => {
          val output = path.resolve("multinli_nli").toFile
          if (output.exists()) {
            // log this
          } else {
            val archiveFile = Utils
              .downloadOneFile("http://www.nyu.edu/projects/bowman/multinli/multinli_1.0.zip", "multinli_nli.zip")(path)
            CompressUtils.decompressZip(archiveFile, path.toFile)
            path.resolve("multinli_1.0").toFile.renameTo(output)
          }
          output
        }),
      snliData
        .dependsOn(dataPath)
        .implBy(path => {
          val output = path.resolve("snli_nli").toFile
          if (output.exists()) {
            // log this
          } else {
            val archiveFile =
              Utils.downloadOneFile("https://nlp.stanford.edu/projects/snli/snli_1.0.zip", "snli_nli.zip")(path)
            CompressUtils.decompressZip(archiveFile, path.toFile)
            path.resolve("snli_1.0").toFile.renameTo(output)
          }
          output
        }),
      downloadData.dependsOn(multiNLIData).implBy(multiNLIData => Seq(multiNLIData))
    )
  }

  object NamedEntityRecognition extends DataDownloader {
    val ontoNoteNERTrainData: Token[File] = Token("OntoNotes NER dataset train data")
    val ontoNoteNERDevData: Token[File] = Token("OntoNotes NER dataset dev data")
    val ontoNoteNERTestData: Token[File] = Token("OntoNotes NER dataset test data")
    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      ontoNoteNERTrainData
        .dependsOn(dataPath)
        .implBy(
          Utils
            .downloadOneFile(
              "http://conll.cemantix.org/2012/download/ids/english/all/train.id",
              "ontonotes_ner_train.id"
            )
        ),
      ontoNoteNERDevData
        .dependsOn(dataPath)
        .implBy(
          Utils
            .downloadOneFile(
              "http://conll.cemantix.org/2012/download/ids/english/all/development.id",
              "ontonotes_ner_dev.id"
            )
        ),
      ontoNoteNERTestData
        .dependsOn(dataPath)
        .implBy(
          Utils
            .downloadOneFile("http://conll.cemantix.org/2012/download/ids/english/all/test.id", "ontonotes_ner_test.id")
        ),
      downloadData.dependsOnAllOf(ontoNoteNERTrainData, ontoNoteNERDevData, ontoNoteNERTestData).implBy(files => files)
    )
  }

  object SentimentAnalysis extends DataDownloader {
    val imdbReviewData: Token[File] = Token("acl imdb review data for sentiment analysis")
    val treeBankSubsetTrainData: Token[File] = Token("Subset of Stanford Treebank train data for sentiment analysis")
    val treeBankSubsetDevData: Token[File] = Token("Subset of Stanford Treebank dev data for sentiment analysis")
    val treeBankSubsetTestData: Token[File] = Token("Subset of Stanford Treebank test data for sentiment analysis")
    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      imdbReviewData
        .dependsOn(dataPath)
        .implBy(path => {
          val output = path.resolve("aclimdb_v1_sa").toFile
          if (output.exists()) {
            // log this
          } else {
            val archiveFile = Utils
              .downloadOneFile(
                "http://ai.stanford.edu/~amaas/data/sentiment/aclImdb_v1.tar.gz",
                "aclimdb_v1_sa.tar.gz"
              )(path)
            CompressUtils.decompressTarGz(archiveFile, path.toFile)
            path.resolve("aclImdb").toFile.renameTo(output)
          }
          output
        }),
      treeBankSubsetTrainData
        .dependsOn(dataPath)
        .implBy(
          Utils.downloadOneFile(
            "https://raw.githubusercontent.com/openai/generating-reviews-discovering-sentiment/master/data/train_binary_sent.csv",
            "sst_sa_train.csv"
          )
        ),
      treeBankSubsetDevData
        .dependsOn(dataPath)
        .implBy(
          Utils.downloadOneFile(
            "https://raw.githubusercontent.com/openai/generating-reviews-discovering-sentiment/master/data/dev_binary_sent.csv",
            "sst_sa_dev.csv"
          )
        ),
      treeBankSubsetTestData
        .dependsOn(dataPath)
        .implBy(
          Utils.downloadOneFile(
            "https://raw.githubusercontent.com/openai/generating-reviews-discovering-sentiment/master/data/test_binary_sent.csv",
            "sst_sa_test.csv"
          )
        ),
      downloadData
        .dependsOnAllOf(treeBankSubsetTrainData, treeBankSubsetDevData, treeBankSubsetTestData, imdbReviewData)
        .implBy(files => files)
    )
  }

  object MachineTranslation extends DataDownloader {
    val iwsltData: Token[Seq[File]] = Token("IWSLT 2016 dataset")
    val machineTranslationTasks: AccumulatorToken[Seq[DecaTask.MachineTranslation]] =
      Token.accumulator("the machine translation language pairs")

    override def extraDataFlows: Seq[DataFlow[_]] = Seq(
      iwsltData
        .dependsOn(dataPath, machineTranslationTasks)
        .implBy((path, machineTranslationTasks) => {
          for (DecaTask.MachineTranslation(source, target) <- machineTranslationTasks) yield {
            val output = path.resolve(s"iwslt_mt_${source}_$target").toFile
            if (output.exists()) {
              // log this
            } else {
              val archive = Utils.downloadOneFile(
                s"https://wit3.fbk.eu/archive/2016-01//texts/$source/$target/$source-$target.tgz",
                s"iwslt-mt-$source-$target.tgz"
              )(path)
              CompressUtils.decompressTarGz(archive, path.toFile)
              // {source}-{target} is the root folder in iwslt data
              path.resolve(s"$source-$target").toFile.renameTo(output)
            }
            output
          }
        }),
      downloadData.dependsOn(iwsltData).implBy(files => files)
    )
  }

}
