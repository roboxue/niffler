package com.example;

import akka.actor.ActorSystem;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.roboxue.niffler.DataFlow;
import com.roboxue.niffler.ExecutionState;
import com.roboxue.niffler.ExecutionStateTracker;
import com.roboxue.niffler.execution.DefaultJavaExecutionLogger;
import com.roboxue.niffler.execution.ExecutionResult;
import com.roboxue.niffler.javaDSL.Niffler;
import org.apache.commons.io.IOUtils;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author robert.xue
 * @since 220
 */
public class JavaExample implements Niffler {

    public static void main(String[] args) {
        Logger logger = Logger.getLogger("JavaExample");
        ExecutionStateTracker st = new ExecutionStateTracker(ExecutionState.empty());
        Iterable<DataFlow<?>> extraInput = Lists.newArrayList(
                JavaExampleTokens.s3Region().initializedTo(Regions.US_WEST_2),
                JavaExampleTokens.s3BucketName().initializedTo("internal-eng-metamind-io"),
                JavaExampleTokens.datasetId().initializedTo("dataset-cars"),
                JavaExampleTokens.changeSetVersion().initializedTo(2),
                JavaExampleTokens.localTempDownloadFolder().initializedTo(Paths.get("/tmp/niffler/download")),
                JavaExampleTokens.localTempOutputFolder().initializedTo(Paths.get("/tmp/niffler/out"))
        );
        ActorSystem system = ActorSystem.create();
        try {
            ExecutionResult<BoxedUnit> result = new JavaExample().asyncRun(
                    JavaExampleTokens.uploadFullDataset(), extraInput, st,
                    new DefaultJavaExecutionLogger(logger))
                    .withAkka(system).await(Duration.Inf());
            result.executionLog().printFlowChartJava(System.out::println);
            result.executionLog().printWaterfallJava(System.out::println);
        } finally {
            system.terminate();
        }
    }

    public static String getS3Path(String datasetId) {
        return String.format("private/yanxue/%s", datasetId);
    }

    public static AmazonS3 getS3Client(AWSCredentialsProvider credentialsProvider, Regions s3Region) {
        return AmazonS3ClientBuilder.standard()
                .withRegion(s3Region)
                .withCredentials(credentialsProvider)
                .build();
    }

    public static AWSCredentialsProvider getCredentialProvider() {
        return new DefaultAWSCredentialsProviderChain();
    }

    public static Path downloadAllChangesets(Path workingDir, AmazonS3 s3, String s3BucketName,
                                             String s3BasePath, Integer changeSetVersion)
            throws IOException {
        workingDir.toFile().mkdirs();
        for (int i = 0; i < changeSetVersion; i++) {
            String changesetZipName = String.format("changeset-%d.zip", i + 1);
            String s3Key = String.format("%s/%s", s3BasePath, changesetZipName);

            Files.copy(
                    s3.getObject(s3BucketName, s3Key).getObjectContent(),
                    workingDir.resolve(changesetZipName),
                    REPLACE_EXISTING
            );
            String changesetJsonName = String.format("changeset%d.jsonl", i + 1);
            s3Key = String.format("%s/%s", s3BasePath, changesetJsonName);
            Files.copy(
                    s3.getObject(s3BucketName, s3Key).getObjectContent(),
                    workingDir.resolve(changesetJsonName),
                    REPLACE_EXISTING
            );
        }
        return workingDir;
    }

    public static Path decompressAllChangesets(Path downloadFolder, Path outputFolder,
                                               Integer changeSetVersion) throws IOException {
        Path outputDirTemp = outputFolder.resolve("temp");
        for (int i = 0; i < changeSetVersion; i++) {
            Path changesetZipName = downloadFolder.resolve(String.format("changeset-%d.zip", i + 1));
            CompressUtils.decompressZip(
                    changesetZipName.toFile(),
                    outputDirTemp.toFile()
            );
        }
        return outputDirTemp;
    }

    public static File createFullJson(Path workingDir, Path outputDir,
                                      Integer changeSetVersion) throws IOException {
        outputDir.toFile().mkdirs();
        Map<String, String> imageLabels = Maps.newHashMap();
        final ObjectMapper mapper = Jackson.getObjectMapper();
        for (int i = 0; i < changeSetVersion; i++) {
            Path changesetJson = workingDir.resolve(String.format("changeset%d.jsonl", i + 1));
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
        File fullJson = outputDir.resolve("full.jsonl").toFile();
        try (PrintWriter pw = new PrintWriter(fullJson)
        ) {
            imageLabels.forEach((key, value) -> {
                ObjectNode o = mapper.createObjectNode();
                o.put("imageName", key);
                o.put("label", value);
                try {
                    pw.println(mapper.writer().writeValueAsString(o));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return fullJson;
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

    public static BoxedUnit uploadMergedDataset(File fullZip, File fullJson, AmazonS3 s3,
                                                String s3BucketName, String s3BasePath) {
        s3.putObject(s3BucketName, String.format("%s/full.zip", s3BasePath), fullZip);
        s3.putObject(s3BucketName, String.format("%s/full.jsonl", s3BasePath), fullJson);
        return BoxedUnit.UNIT;
    }

    @Override
    public ImmutableList<DataFlow<?>> getDataFlows() {
        return ImmutableList.of(
                JavaExampleTokens.s3BasePath()
                        .dependsOnJ(
                                JavaExampleTokens.datasetId())
                        .implBy(JavaExample::getS3Path),
                JavaExampleTokens.s3Client()
                        .dependsOnJ(
                                JavaExampleTokens.awsCredentialProvider(),
                                JavaExampleTokens.s3Region())
                        .implBy(JavaExample::getS3Client),
                JavaExampleTokens.awsCredentialProvider()
                        .implBy(JavaExample::getCredentialProvider),
                JavaExampleTokens.downloadDataset()
                        .dependsOnJ(
                                JavaExampleTokens.localTempDownloadFolder(),
                                JavaExampleTokens.s3Client(),
                                JavaExampleTokens.s3BucketName(),
                                JavaExampleTokens.s3BasePath(),
                                JavaExampleTokens.changeSetVersion())
                        .implBy(JavaExample::downloadAllChangesets),
                JavaExampleTokens.decompressedDataset()
                        .dependsOnJ(
                                JavaExampleTokens.downloadDataset(),
                                JavaExampleTokens.localTempOutputFolder(),
                                JavaExampleTokens.changeSetVersion())
                        .implBy(JavaExample::decompressAllChangesets),
                JavaExampleTokens.fullJson()
                        .dependsOnJ(
                                JavaExampleTokens.downloadDataset(),
                                JavaExampleTokens.localTempOutputFolder(),
                                JavaExampleTokens.changeSetVersion())
                        .implBy(JavaExample::createFullJson),
                JavaExampleTokens.fullZip()
                        .dependsOnJ(
                                JavaExampleTokens.decompressedDataset(),
                                JavaExampleTokens.localTempOutputFolder())
                        .implBy(JavaExample::createFullZip),
                JavaExampleTokens.uploadFullDataset()
                        .dependsOnJ(
                                JavaExampleTokens.fullZip(),
                                JavaExampleTokens.fullJson(),
                                JavaExampleTokens.s3Client(),
                                JavaExampleTokens.s3BucketName(),
                                JavaExampleTokens.s3BasePath())
                        .implBy(JavaExample::uploadMergedDataset)
        );
    }
}
