package com.example;

import akka.actor.ActorSystem;
import com.amazonaws.regions.Regions;
import com.example.logging.NoFormatter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.roboxue.niffler.DataFlow;
import com.roboxue.niffler.ExecutionState;
import com.roboxue.niffler.ExecutionStateTracker;
import com.roboxue.niffler.execution.ExecutionLogger;
import com.roboxue.niffler.execution.WaterfallExecutionLogger;
import com.roboxue.niffler.javaDSL.Niffler;
import java.nio.file.Paths;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import scala.concurrent.duration.Duration;

/**
 * @author robert.xue
 * @since 220
 */
public class JavaExample implements Niffler {

    @Override
    public ImmutableList<DataFlow<?>> getDataFlows() {
        return ImmutableList.of(
                Contract.s3BasePath()
                        .dependsOnJ(
                                Contract.datasetId())
                        .implBy(JavaCoding::getS3Path),
                Contract.s3Client()
                        .dependsOnJ(
                                Contract.awsCredentialProvider(),
                                Contract.s3Region())
                        .implBy(JavaCoding::getS3Client),
                Contract.awsCredentialProvider()
                        .implBy(JavaCoding::getCredentialProvider),
                Contract.downloadDataset()
                        .dependsOnJ(
                                Contract.localTempDownloadFolder(),
                                Contract.s3Client(),
                                Contract.s3BucketName(),
                                Contract.s3BasePath(),
                                Contract.changeSetVersion())
                        .implBy(JavaCoding::downloadAllChangesets),
                Contract.decompressedDataset()
                        .dependsOnJ(
                                Contract.downloadDataset(),
                                Contract.localTempOutputFolder(),
                                Contract.changeSetVersion())
                        .implBy(JavaCoding::decompressAllChangesets),
                Contract.fullJson()
                        .dependsOnJ(
                                Contract.downloadDataset(),
                                Contract.localTempOutputFolder(),
                                Contract.changeSetVersion())
                        .implBy(JavaCoding::createFullJson),
                Contract.fullZip()
                        .dependsOnJ(
                                Contract.decompressedDataset(),
                                Contract.localTempOutputFolder())
                        .implBy(JavaCoding::createFullZip),
                Contract.uploadFullDataset()
                        .dependsOnJ(
                                Contract.fullZip(),
                                Contract.fullJson(),
                                Contract.s3Client(),
                                Contract.s3BucketName(),
                                Contract.s3BasePath())
                        .implBy(JavaCoding::uploadMergedDataset)
        );
    }

    public static void main(String[] args) {
        Logger logger = Logger.getLogger(JavaExample.class.getName());
        Handler h = new ConsoleHandler();
        h.setFormatter(new NoFormatter());
        logger.addHandler(h);
        logger.setUseParentHandlers(false);

        JavaExample exampleDataflow = new JavaExample();
        exampleDataflow.printGraph(logger, true);
        System.out.println();
        exampleDataflow.printGraph(logger, false);
        System.out.println();

        Iterable<DataFlow<?>> extraInput = Lists.newArrayList(
                Contract.s3Region().initializedTo(Regions.US_WEST_2),
                Contract.s3BucketName().initializedTo("internal-eng-metamind-io"),
                Contract.datasetId().initializedTo("dataset-cars"),
                Contract.changeSetVersion().initializedTo(2),
                Contract.localTempDownloadFolder()
                        .initializedTo(Paths.get("/tmp/niffler/download")),
                Contract.localTempOutputFolder().initializedTo(Paths.get("/tmp/niffler/out"))
        );
        exampleDataflow.printGraph(logger, false, extraInput);
        System.out.println();

        ExecutionLogger nifflerLogger = new WaterfallExecutionLogger(logger, Level.INFO);
//        ExecutionLogger nifflerLogger = new FlowChartExecutionLogger(logger, Level.INFO);
        ActorSystem system = ActorSystem.create();
        ExecutionStateTracker st = new ExecutionStateTracker(ExecutionState.empty());
        try {
            exampleDataflow
                    .asyncRun(Contract.uploadFullDataset(), extraInput, st, nifflerLogger)
                    .withAkka(system)
                    .await(Duration.Inf());
        } finally {
            system.terminate();
        }
    }
}
