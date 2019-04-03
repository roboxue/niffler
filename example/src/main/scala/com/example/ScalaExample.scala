package com.example

import java.nio.file.Paths
import java.util.logging.{ConsoleHandler, Handler, Level, Logger}

import akka.actor.ActorSystem
import com.amazonaws.regions.Regions
import com.example.logging.NoFormatter
import com.roboxue.niffler.execution.{ExecutionLogger, FlowChartExecutionLogger, WaterfallExecutionLogger}
import com.roboxue.niffler.{DataFlow, ExecutionStateTracker}
import com.roboxue.niffler.scalaDSL.Niffler

import scala.concurrent.duration.Duration

/**
  * @author robert.xue
  * @since 220
  */
class ScalaExample extends Niffler {
  override def dataFlows: Iterable[DataFlow[_]] = Seq(
    Contract.s3BasePath
      .dependsOn(Contract.datasetId)
      .implBy(JavaCoding.getS3Path),
    Contract.s3Client
      .dependsOn(Contract.awsCredentialProvider, Contract.s3Region)
      .implBy(JavaCoding.getS3Client),
    Contract.awsCredentialProvider
      .implBy(JavaCoding.getCredentialProvider),
    Contract.downloadDataset
      .dependsOn(
        Contract.localTempDownloadFolder,
        Contract.s3Client,
        Contract.s3BucketName,
        Contract.s3BasePath,
        Contract.changeSetVersion
      )
      .implBy(JavaCoding.downloadAllChangesets),
    Contract.decompressedDataset
      .dependsOn(Contract.downloadDataset, Contract.localTempOutputFolder, Contract.changeSetVersion)
      .implBy(JavaCoding.decompressAllChangesets),
    Contract.fullJson
      .dependsOn(Contract.downloadDataset, Contract.localTempOutputFolder, Contract.changeSetVersion)
      .implBy(JavaCoding.createFullJson),
    Contract.fullZip
      .dependsOn(Contract.decompressedDataset, Contract.localTempOutputFolder)
      .implBy(JavaCoding.createFullZip),
    Contract.uploadFullDataset
      .dependsOn(Contract.fullZip, Contract.fullJson, Contract.s3Client, Contract.s3BucketName, Contract.s3BasePath)
      .implBy(JavaCoding.uploadMergedDataset)
  )
}

object ScalaExample {

  def main(args: Array[String]): Unit = {
    val logger = Logger.getLogger(classOf[JavaExample].getName)
    val h = new ConsoleHandler
    h.setFormatter(new NoFormatter)
    logger.addHandler(h)
    logger.setUseParentHandlers(false)

    val exampleDataflow = new ScalaExample()
    exampleDataflow.printGraph(logger, useCodeName = true)
    println()
    exampleDataflow.printGraph(logger, useCodeName = false)
    println()

    val extraInput = Seq(
      Contract.s3Region.initializedTo(Regions.US_WEST_2),
      Contract.s3BucketName.initializedTo("internal-eng-metamind-io"),
      Contract.datasetId.initializedTo("dataset-cars"),
      Contract.changeSetVersion.initializedTo(2),
      Contract.localTempDownloadFolder.initializedTo(Paths.get("/tmp/niffler/download")),
      Contract.localTempOutputFolder.initializedTo(Paths.get("/tmp/niffler/out"))
    )
    exampleDataflow.printGraph(logger, useCodeName = true, extraInput)
    println()

    val nifflerLogger = new WaterfallExecutionLogger(logger)
//    val nifflerLogger = new FlowChartLogger(logger)
    val system = ActorSystem.create()
    val st = new ExecutionStateTracker()
    try {
      exampleDataflow
        .asyncRun(Contract.uploadFullDataset, extraInput, Some(nifflerLogger))(st)
        .withAkka(system)
        .await(Duration.Inf)
    } finally {
      system.terminate()
    }
  }
}
