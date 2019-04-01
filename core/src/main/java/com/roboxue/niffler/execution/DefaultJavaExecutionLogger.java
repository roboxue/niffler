package com.roboxue.niffler.execution;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author robert.xue
 * @since 220
 */
public class DefaultJavaExecutionLogger implements ExecutionLogger {

  private final Logger logger;

  public DefaultJavaExecutionLogger(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void onLogStarted(LogStarted event) {
    logger.info(String.format("%s [%s][LogStarted]",
        Long.toString(event.timestamp()),
        Integer.toString(event.executionId())));
  }

  @Override
  public void onTokenAnalyzed(TokenAnalyzed event) {
    logger.info(String.format("%s [%s][TokenAnalyzed] %s",
        Long.toString(event.timestamp()),
        Integer.toString(event.executionId()),
        event.token().codeName())
    );
  }

  @Override
  public void onTokenBacklogged(TokenBacklogged event) {
    logger.info(String.format("%s [%s][TokenBacklogged] %s because of %d blockers",
        Long.toString(event.timestamp()),
        Integer.toString(event.executionId()),
        event.token().codeName(),
        event.unmet().size()
    ));
  }

  @Override
  public void onTokenStartedEvaluation(TokenStartedEvaluation event) {
    logger.info(String.format("%s [%s][TokenStartedEvaluation] %s",
        Long.toString(event.timestamp()),
        Integer.toString(event.executionId()),
        event.token().codeName())
    );
  }

  @Override
  public void onTokenEndedEvaluation(TokenEndedEvaluation event) {
    logger.info(String.format("%s [%s][TokenEndedEvaluation] %s",
        Long.toString(event.timestamp()),
        Integer.toString(event.executionId()),
        event.token().codeName())
    );
  }

  @Override
  public void onTokenRevisited(TokenRevisited event) {
    logger.info(String.format("%s [%s][TokenRevisited] %s because of blocker %s has been resolved",
        Long.toString(event.timestamp()),
        Integer.toString(event.executionId()),
        event.token().codeName(),
        event.blockerRemoved().codeName()
    ));
  }

  @Override
  public void onTokenFailedEvaluation(TokenFailedEvaluation event) {
    logger.log(Level.INFO, String.format("%s [%s][TokenFailedEvaluation] %s",
        Long.toString(event.timestamp()),
        Integer.toString(event.executionId()),
        event.token().codeName()
    ), event.ex());
  }

  @Override
  public void onTokenCancelledEvaluation(TokenCancelledEvaluation event) {
    if (event.canceledBecause().isDefined()) {
      logger.info(String.format("%s [%s][TokenCancelledEvaluation] %s due to issues in blocker %s",
          Long.toString(event.timestamp()),
          Integer.toString(event.executionId()),
          event.token().codeName(),
          event.canceledBecause().get().codeName()));
    } else {
      logger.info(String.format("%s [%s][TokenCancelledEvaluation] %s",
          Long.toString(event.timestamp()),
          Integer.toString(event.executionId()),
          event.token().codeName()));
    }
  }

  @Override
  public void onLogEnded(LogEnded event) {
    logger.info(String.format("%s [%s][LogEnded]",
        Long.toString(event.timestamp()),
        Integer.toString(event.executionId())));
  }
}
