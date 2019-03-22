package com.example;

import akka.actor.ActorSystem;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.roboxue.decanlp.CompressUtils;
import com.roboxue.niffler.DataFlow;
import com.roboxue.niffler.ExecutionState;
import com.roboxue.niffler.ExecutionStateTracker;
import com.roboxue.niffler.execution.ExecutionResult;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.IOUtils;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

/**
 * @author robert.xue
 * @since 220
 */
public class JavaExample {

  public static void main(String[] args) {
    ExecutionStateTracker st = new ExecutionStateTracker(ExecutionState.empty());
    Iterable<DataFlow<?>> extraInput = Lists.newArrayList(

    );
    ActorSystem system = ActorSystem.create();
    ExecutionResult<BoxedUnit> result = JavaExampleTokens
        .asyncRun(JavaExampleTokens.uploadFullDataset(), extraInput, st)
        .withAkka(system).await(Duration.Inf());
    result.executionLog().printFlowChartJava(System.out::println);
  }

  public static String getS3Path(String datasetId) {
    return String.format("private/yanxue/%s", datasetId);
  }

  public static AmazonS3 getS3Client(String region, AWSCredentialsProvider credentialsProvider) {
    return AmazonS3ClientBuilder.standard()
        .withRegion(region)
        .withCredentials(credentialsProvider)
        .build();
  }

  public static AWSCredentialsProvider getCredentialProvider() {
    return new DefaultAWSCredentialsProviderChain();
  }

  public static Path downloadAllChangesets(Path workingDir, AmazonS3 s3, String s3BucketName,
      String s3BasePath, Integer changeSetVersion)
      throws IOException {
    for (int i = 0; i < changeSetVersion; i++) {
      String changesetZipName = String.format("changeset-%d.zip", i);
      Files.copy(
          s3.getObject(s3BucketName, String.format("%s/%s", s3BasePath, changesetZipName)).getObjectContent(),
          workingDir.resolve(changesetZipName)
      );
      String changesetJsonName = String.format("changeset-%d.json", i);
      Files.copy(
          s3.getObject(s3BucketName, String.format("%s/%s", s3BasePath, changesetJsonName)).getObjectContent(),
          workingDir.resolve(changesetJsonName)
      );
    }
    return workingDir;
  }

  public static Path decompressAllChangesets(Path downloadFolder, Path outputFolder,
      Integer changeSetVersion) throws IOException {
    Path outputDirTemp = outputFolder.resolve("temp");
    for (int i = 0; i < changeSetVersion; i++) {
      Path changesetZipName = downloadFolder.resolve(String.format("changeset-%d.zip", i));
      CompressUtils.decompressZip(
          changesetZipName.toFile(),
          outputDirTemp.toFile()
      );
    }
    return outputDirTemp;
  }

  public static File createFullJson(Path workingDir, Path outputDir,
      Integer changeSetVersion) throws IOException {
    Map<String, String> imageLabels = Maps.newHashMap();
    final ObjectMapper mapper = Jackson.getObjectMapper();
    for (int i = 0; i < changeSetVersion; i++) {
      Path changesetJson = workingDir.resolve(String.format("changeset-%d.jsonl", i));
      IOUtils.readLines(new FileInputStream(changesetJson.toFile()), Charset.defaultCharset())
          .forEach(line -> {
            try {
              ObjectNode l = mapper.readValue(line, ObjectNode.class);
              imageLabels.put(l.get("imageName").asText(), l.get("label").asText());
            } catch (IOException ignored) {
            }
          });

    }

    // create full.json
    Path fullJson = outputDir.resolve("full.jsonl");
    try (FileOutputStream fos = new FileOutputStream(fullJson.toString());
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos))
    ) {
      imageLabels.forEach((key, value) -> {
        ObjectNode o = mapper.createObjectNode();
        o.put("imageName", key);
        o.put("label", value);
        try {
          mapper.writer().writeValue(bw, o);
          bw.newLine();
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    }
    return outputDir.toFile();
  }

  public static File createFullZip(Path workingDir, Path outputDir)
      throws IOException {
    // create full.zip
    Path fullZip = outputDir.resolve("full.zip");
    ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(fullZip.toString()));
    Files.walkFileTree(workingDir, new SimpleFileVisitor<Path>() {
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        zos.putNextEntry(new ZipEntry(workingDir.relativize(file).toString()));
        Files.copy(file, zos);
        zos.closeEntry();
        return FileVisitResult.CONTINUE;
      }
    });
    zos.close();
    return fullZip.toFile();
  }

  public static void uploadMergedDataset(File fullZip, File fullJson, AmazonS3 s3,
      String s3BucketName, String s3BasePath) {
    s3.putObject(s3BucketName, String.format("%s/full.zip", s3BasePath), fullZip);
    s3.putObject(s3BucketName, String.format("%s/full.json", s3BasePath), fullJson);
  }

}
