package com.roboxue.decanlp
import java.net.URI
import java.nio.file.{Files, Path}

import com.roboxue.niffler.{DataFlow, Logic, Niffler, Token}
import org.json4s.Formats
import org.json4s.jackson.JsonMethods._

import scala.collection.JavaConverters._

object QuestionAnsweringLoader extends Niffler {
  val squadFileList: Token[Seq[DownloadInstruction]] = Token("squad data download links")
  val maxSample: Token[Long] = Token("the max number of data to be loaded from each datasource")
  val tokenizer: Token[Sentence => Seq[String]] = Token("tokenize algorithm")

  val rawDataDownloadLocation: Token[Path] = Token("the base directory to save squad data")
  val compiledDataSaveLocation: Token[Path] = Token("the base directory to save processed data")

  val compileTrainData: Token[DecaDataset] = Token("convert squad data to question answer context data")
  val compileValidationData: Token[DecaDataset] = Token("convert squad data to question answer context data")

  val tokenizedTrainData: Token[DecaTokenizedDataset] = Token("tokenized train data")
  val tokenizedValidationData: Token[DecaTokenizedDataset] = Token("tokenized validation data")

  val saveCompiledData: Token[Unit] = Token("save processed squad data to processed data folder")

  val downloadData: Token[Path] = Token("download squad data to rawData folder")
  val cleanDownload: Token[Unit] = Token("remove existing download if exists")
  val cleanCompiled: Token[Unit] = Token("remove existing compiled data if exits")

  val V1_1: Seq[DownloadInstruction] = Seq(
    DownloadInstruction(URI.create("https://rajpurkar.github.io/SQuAD-explorer/dataset/train-v1.1.json"), "train.json"),
    DownloadInstruction(URI.create("https://rajpurkar.github.io/SQuAD-explorer/dataset/dev-v1.1.json"), "dev.json"),
  )

  val V2_0: Seq[DownloadInstruction] = Seq(
    DownloadInstruction(URI.create("https://rajpurkar.github.io/SQuAD-explorer/dataset/train-v2.0.json"), "train.json"),
    DownloadInstruction(URI.create("https://rajpurkar.github.io/SQuAD-explorer/dataset/dev-v2.0.json"), "dev.json"),
  )

  override def dataFlows: Iterable[DataFlow[_]] = Seq(
    squadFileList := V2_0,
    maxSample := 200000,
    tokenizer := { a =>
      RevtokTokenizer.tokenize(a, false, true).asScala
    },
    cleanCompiled.dependsOn(compiledDataSaveLocation).implBy(recursiveDelete),
    cleanDownload.dependsOn(rawDataDownloadLocation).implBy(recursiveDelete),
    downloadData.dependsOn(squadFileList, rawDataDownloadLocation).implBy(doDownloadData),
    compileTrainData
      .dependsOn(downloadData)
      .implBy({ path =>
        loadFromSQuAD(path.resolve("train.json"))
      }),
    compileValidationData
      .dependsOn(downloadData)
      .implBy({ path =>
        loadFromSQuAD(path.resolve("dev.json"))
      }),
    saveCompiledData
      .dependsOn(compileTrainData, compileValidationData, compiledDataSaveLocation)
      .implBy({ (trainData, validationData, saveLocation) =>
        trainData.writeTo(saveLocation.resolve(TRAIN_DATA_FILENAME).toFile)
        validationData.writeTo(saveLocation.resolve(VALIDATION_DATA_FILENAME).toFile)
      }),
    tokenizedTrainData
      .dependsOn(compileTrainData, tokenizer)
      .implBy({ (dataset, tokenizer) =>
        DecaTokenizedDataset(dataset.records.map(_.tokenize(tokenizer)))
      }),
    tokenizedValidationData
      .dependsOn(compileValidationData, tokenizer)
      .implBy({ (dataset, tokenizer) =>
        DecaTokenizedDataset(dataset.records.map(_.tokenize(tokenizer)))
      }),
  )

  private def recursiveDelete(dir: Path): Unit = {
    if (Files.exists(dir)) {
      if (Files.isDirectory(dir)) {
        Files.walk(dir).parallel().forEach(Files.delete _)
      }
      Files.delete(dir)
    }
  }

  private def download(from: URI, to: Path): Unit = {
    // TODO: Implement me
    ???
  }

  private def doDownloadData(fileList: Seq[DownloadInstruction], folder: Path): Path = {
    Files.createDirectories(folder)
    for (instruction <- fileList) {
      val target = folder.resolve(instruction.fileName)
      if (!Files.exists(target)) {
        download(instruction.uri, target)
      }
    }
    folder
  }

  private def loadFromSQuAD(document: Path): DecaDataset = {
    implicit val format: Formats = org.json4s.DefaultFormats
    val paragraphs = (parse(document.toFile) \ "data" \ "paragraphs").extract[Seq[SquadParagraph]]
    var squadIndex = 0
    val records = for (p <- paragraphs; qa <- p.qas) yield {
      val context = replaceWhiteSpace(p.context)
      val question = replaceWhiteSpace(qa.question)
      squadIndex += 1
      if (qa.answers.isEmpty) {
        SquadRawInput(qa.id, squadIndex, question, context, "unanswerable")
      } else {
        val answer = replaceWhiteSpace(qa.answers.head.text)
        SquadRawInput(qa.id, squadIndex, question, context, answer)
      }
    }
    DecaDataset(records)
  }

  private def replaceWhiteSpace(str: String): String = {
    str.split("%s").mkString(" ")
  }

  case class SquadParagraph(context: String, qas: Seq[SquadQA])
  case class SquadQA(question: String, id: String, answers: Seq[SquadAnswer])
  case class SquadAnswer(text: String, answer_start: Int)
  case class SquadRawInput(squadId: String, squadIndex: Int, question: String, context: String, answer: String)
      extends DecaRawInput
}
