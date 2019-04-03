package com.roboxue.niffler.javaDSL;

import com.google.common.collect.Iterables;
import com.roboxue.niffler.DataFlow;
import com.roboxue.niffler.ExecutionStateTracker;
import com.roboxue.niffler.LanguageBridge;
import com.roboxue.niffler.Logic;
import com.roboxue.niffler.Token;
import com.roboxue.niffler.execution.AsyncExecution;
import com.roboxue.niffler.execution.ExecutionLogger;

import javax.annotation.Nullable;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface Niffler {

    Iterable<DataFlow<?>> getDataFlows();

    default <T> AsyncExecution<T> asyncRun(Token<T> token, Iterable<DataFlow<?>> extraFlow,
            ExecutionStateTracker sc, @Nullable ExecutionLogger logger) {
        return LanguageBridge.asyncRun(this, token, extraFlow, sc, logger);
    }

    default void printGraph(Logger logger, boolean useCodeName) {
        new Logic(LanguageBridge.javaIterableAsSeq(getDataFlows()))
                .printFlowChart(logger, Level.INFO, useCodeName);
    }

    default void printGraph(Logger logger, boolean useCodeName, Iterable<DataFlow<?>> extraFlows) {
        new Logic(LanguageBridge.javaIterableAsSeq(Iterables.concat(getDataFlows(), extraFlows)))
                .printFlowChart(logger, Level.INFO, useCodeName);
    }
}
