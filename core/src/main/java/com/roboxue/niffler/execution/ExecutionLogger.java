package com.roboxue.niffler.execution;

/**
 * @author robert.xue
 * @since 220
 */
public interface ExecutionLogger {
  void onLogStarted(LogStarted event);
  void onTokenAnalyzed(TokenAnalyzed event);
  void onTokenBacklogged(TokenBacklogged event);
  void onTokenStartedEvaluation(TokenStartedEvaluation event);
  void onTokenEndedEvaluation(TokenEndedEvaluation event);
  void onTokenRevisited(TokenRevisited event);
  void onTokenFailedEvaluation(TokenFailedEvaluation event);
  void onTokenCancelledEvaluation(TokenCancelledEvaluation event);
  void onLogEnded(LogEnded event);
}
