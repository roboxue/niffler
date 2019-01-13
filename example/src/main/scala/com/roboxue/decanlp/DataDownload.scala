package com.roboxue.decanlp
import java.io.File
import java.nio.file.Path

import com.roboxue.niffler._

import scala.collection.mutable.ListBuffer

class DataDownload(decaTasks: Seq[DecaTask]) extends Niffler {

  override val logic: Logic = {
    val dataFlows = ListBuffer[DataFlow[_]]()
    decaTasks.foreach({
      case DecaTask.QuestionAnswering =>
        dataFlows ++= QuestionAnswering.dataFlows
        dataFlows ++= Seq(
          QuestionAnswering.dataPath.dependsOn(DataDownload.dataPath).implBy(_.resolve("question_answering")),
          DataDownload.downloadedData ++= QuestionAnswering.downloadData
        )
      case DecaTask.Summarization =>
        dataFlows ++= Summarization.dataFlows
        dataFlows ++= Seq(
          Summarization.dataPath.dependsOn(DataDownload.dataPath).implBy(_.resolve("summarization")),
          DataDownload.downloadedData ++= Summarization.downloadData
        )
      case DecaTask.SemanticParsing =>
        dataFlows ++= SemanticParsing.dataFlows
        dataFlows ++= Seq(
          SemanticParsing.dataPath.dependsOn(DataDownload.dataPath).implBy(_.resolve("semantic_parsing")),
          DataDownload.downloadedData ++= SemanticParsing.downloadData
        )
      case DecaTask.SemanticRoleLabeling =>
        dataFlows ++= SemanticRoleLabeling.dataFlows
        dataFlows ++= Seq(
          SemanticRoleLabeling.dataPath.dependsOn(DataDownload.dataPath).implBy(_.resolve("semantic_role_labeling")),
          DataDownload.downloadedData ++= SemanticRoleLabeling.downloadData
        )
      case DecaTask.CommonsenseReasoning =>
        dataFlows ++= CommonsenseReasoning.dataFlows
        dataFlows ++= Seq(
          CommonsenseReasoning.dataPath.dependsOn(DataDownload.dataPath).implBy(_.resolve("commonsense_reasoning")),
          DataDownload.downloadedData ++= CommonsenseReasoning.downloadData
        )
      case DecaTask.GoalOrientedDialogue =>
        dataFlows ++= GoalOrientedDialogue.dataFlows
        dataFlows ++= Seq(
          GoalOrientedDialogue.dataPath.dependsOn(DataDownload.dataPath).implBy(_.resolve("goal_oriented_dialogue")),
          DataDownload.downloadedData ++= GoalOrientedDialogue.downloadData
        )
      case DecaTask.NaturalLanguageInference =>
        dataFlows ++= NaturalLanguageInference.dataFlows
        dataFlows ++= Seq(
          NaturalLanguageInference.dataPath
            .dependsOn(DataDownload.dataPath)
            .implBy(_.resolve("natural_language_inference")),
          DataDownload.downloadedData ++= NaturalLanguageInference.downloadData,
        )
      case DecaTask.RelationExtraction =>
        dataFlows ++= RelationExtraction.dataFlows
        dataFlows ++= Seq(
          RelationExtraction.dataPath.dependsOn(DataDownload.dataPath).implBy(_.resolve("relation_extraction")),
          DataDownload.downloadedData ++= RelationExtraction.downloadData,
        )
      case DecaTask.NamedEntityRecognition =>
        dataFlows ++= NamedEntityRecognition.dataFlows
        dataFlows ++= Seq(
          NamedEntityRecognition.dataPath
            .dependsOn(DataDownload.dataPath)
            .implBy(_.resolve("named_entity_recognition")),
          DataDownload.downloadedData ++= NamedEntityRecognition.downloadData,
        )
      case DecaTask.SentimentAnalysis =>
        dataFlows ++= SentimentAnalysis.dataFlows
        dataFlows ++= Seq(
          SentimentAnalysis.dataPath.dependsOn(DataDownload.dataPath).implBy(_.resolve("sentiment_analysis")),
          DataDownload.downloadedData ++= SentimentAnalysis.downloadData,
        )
      case t @ DecaTask.MachineTranslation(source, target) =>
        dataFlows ++= MachineTranslation.dataFlows
        dataFlows ++= Seq(
          MachineTranslation.dataPath
            .dependsOn(DataDownload.dataPath)
            .implBy(_.resolve(s"machine_translation")),
          MachineTranslation.machineTranslationTasks += t,
          DataDownload.downloadedData ++= MachineTranslation.downloadData,
        )
    })
    new Logic(dataFlows)
  }

}

object DataDownload {
  val downloadedData: AccumulatorToken[Seq[File]] = Token.accumulator("all downloaded datasets")
  val dataPath: Token[Path] = Token("the working folder for dataset download")
}

trait DataDownloader {
  private lazy val clazzName = getClass.getSimpleName.stripSuffix("$")
  lazy val dataPath: Token[Path] = Token(s"the working folder for $clazzName dataset")
  lazy val downloadData: Token[Seq[File]] = Token(s"perform download for $clazzName")

  def extraDataFlows: Seq[DataFlow[_]]

  final def dataFlows: Seq[DataFlow[_]] = extraDataFlows
}

object QuestionAnswering extends DataDownloader {

  val squadTrainJson: Token[File] = Token("the location of the train.json of squad v2")
  val squadDevJson: Token[File] = Token("the location of the dev.json of squad v2")

  override def extraDataFlows: Seq[DataFlow[_]] = Seq(
    squadTrainJson
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils
          .downloadOneFile(
            s"https://rajpurkar.github.io/SQuAD-explorer/dataset/train-v2.0.json",
            "squad_v2_qa_train.json"
          )
      ),
    squadDevJson
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils
          .downloadOneFile(s"https://rajpurkar.github.io/SQuAD-explorer/dataset/dev-v2.0.json", "squad_v2_qa_dev.json")
      ),
    downloadData
      .dependsOnAllOf(squadTrainJson, squadDevJson)
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
            DownloadUtils.downloadFromGoogleDrive(
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
            DownloadUtils.downloadFromGoogleDrive(
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
          val archiveFile = DownloadUtils
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
        DownloadUtils.downloadOneFile("https://dada.cs.washington.edu/qasrl/data/wiki1.train.qa", "qasrl_srl_train.qa")
      ),
    testData
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils.downloadOneFile("https://dada.cs.washington.edu/qasrl/data/wiki1.test.qa", "qasrl_srl_test.qa")
      ),
    devData
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils.downloadOneFile("https://dada.cs.washington.edu/qasrl/data/wiki1.dev.qa", "qasrl_srl_dev.qa")
      ),
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
        DownloadUtils
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
          DownloadUtils.downloadOneFile(
            "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_train_en.json",
            "woz_god_train_en.qa"
          )
        ),
      wozValidationDataEn
        .dependsOn(dataPath)
        .implBy(
          DownloadUtils.downloadOneFile(
            "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_validate_en.json",
            "woz_god_validate_en.qa"
          )
        ),
      wozTestDataEn
        .dependsOn(dataPath)
        .implBy(
          DownloadUtils.downloadOneFile(
            "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_test_en.json",
            "woz_god_test_en.qa"
          )
        ),
      wozTrainDataDe
        .dependsOn(dataPath)
        .implBy(
          DownloadUtils.downloadOneFile(
            "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_train_de.json",
            "woz_god_train_de.qa"
          )
        ),
      wozValidationDataDe
        .dependsOn(dataPath)
        .implBy(
          DownloadUtils.downloadOneFile(
            "https://raw.githubusercontent.com/nmrksic/neural-belief-tracker/master/data/woz/woz_validate_de.json",
            "woz_god_validate_de.qa"
          )
        ),
      wozTestDataDe
        .dependsOn(dataPath)
        .implBy(
          DownloadUtils.downloadOneFile(
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
  override def extraDataFlows: Seq[DataFlow[_]] = Seq(
    multiNLIData
      .dependsOn(dataPath)
      .implBy(path => {
        val output = path.resolve("multinli_nli").toFile
        if (output.exists()) {
          // log this
        } else {
          val archiveFile = DownloadUtils
            .downloadOneFile("http://www.nyu.edu/projects/bowman/multinli/multinli_1.0.zip", "multinli_nli.zip")(path)
          CompressUtils.decompressZip(archiveFile, path.toFile)
          path.resolve("multinli_1.0").toFile.renameTo(output)
        }
        output
      }),
    downloadData.dependsOn(multiNLIData).implBy(multiNLIData => Seq(multiNLIData))
  )
}

object RelationExtraction extends DataDownloader {
  val zeroShotREData: Token[File] = Token("zero shot relation extraction dataset")
  override def extraDataFlows: Seq[DataFlow[_]] = Seq(
    zeroShotREData
      .dependsOn(dataPath)
      .implBy(path => {
        val output = path.resolve("zeroshot_re").toFile
        if (output.exists()) {
          // log this
        } else {
          val archiveFile = DownloadUtils
            .downloadOneFile("http://nlp.cs.washington.edu/zeroshot/relation_splits.tar.bz2", "zeroshot_re.tar.bz2")(
              path
            )
          CompressUtils.decompressTarBz(archiveFile, path.toFile)
          path.resolve("relation_splits").toFile.renameTo(output)
        }
        output
      }),
    downloadData.dependsOn(zeroShotREData).implBy(zeroShotREData => Seq(zeroShotREData))
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
        DownloadUtils
          .downloadOneFile("http://conll.cemantix.org/2012/download/ids/english/all/train.id", "ontonotes_ner_train.id")
      ),
    ontoNoteNERDevData
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils
          .downloadOneFile(
            "http://conll.cemantix.org/2012/download/ids/english/all/development.id",
            "ontonotes_ner_dev.id"
          )
      ),
    ontoNoteNERTestData
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils
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
          val archiveFile = DownloadUtils
            .downloadOneFile("http://ai.stanford.edu/~amaas/data/sentiment/aclImdb_v1.tar.gz", "aclimdb_v1_sa.tar.gz")(
              path
            )
          CompressUtils.decompressTarGz(archiveFile, path.toFile)
          path.resolve("aclImdb").toFile.renameTo(output)
        }
        output
      }),
    treeBankSubsetTrainData
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils.downloadOneFile(
          "https://raw.githubusercontent.com/openai/generating-reviews-discovering-sentiment/master/data/train_binary_sent.csv",
          "sst_sa_train.csv"
        )
      ),
    treeBankSubsetDevData
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils.downloadOneFile(
          "https://raw.githubusercontent.com/openai/generating-reviews-discovering-sentiment/master/data/dev_binary_sent.csv",
          "sst_sa_dev.csv"
        )
      ),
    treeBankSubsetTestData
      .dependsOn(dataPath)
      .implBy(
        DownloadUtils.downloadOneFile(
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
            val archive = DownloadUtils.downloadOneFile(
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
