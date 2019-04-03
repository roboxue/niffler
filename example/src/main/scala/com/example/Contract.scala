package com.example

import java.io.File
import java.nio.file.Path

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.roboxue.niffler.Token

/**
  * @author robert.xue
  * @since 220
  */
object Contract {
  val datasetId: Token[String] = Token("datasetId")
  val changeSetVersion: Token[Integer] = Token("number of change sets to download")
  val s3BucketName: Token[String] = Token("s3 bucket name that holds the data")
  val awsCredentialProvider: Token[AWSCredentialsProvider] = Token("aws credentials provider")
  val localTempDownloadFolder: Token[Path] = Token("working directory for download")
  val localTempOutputFolder: Token[Path] = Token("working directory for output")
  val s3Client: Token[AmazonS3] = Token("s3 client")
  val s3Region: Token[Regions] = Token("s3 region")

  val s3BasePath: Token[String] = Token("the base path of the dataset")
  val downloadDataset: Token[Path] = Token("directory that contains downloaded data")
  val decompressedDataset: Token[Path] = Token("directory that contains decompressed data")
  val fullZip: Token[File] = Token("one zip file that contains the merged data")
  val fullJson: Token[File] = Token("one json file that contains the merged metadata")
  val uploadFullDataset: Token[Unit] = Token("upload full.zip and full.json")
}
