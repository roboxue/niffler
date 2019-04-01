package com.roboxue.niffler.javaDSL;

import com.roboxue.niffler.DataFlow;
import com.roboxue.niffler.ExecutionStateTracker;
import com.roboxue.niffler.LanguageBridge;
import com.roboxue.niffler.Token;
import com.roboxue.niffler.execution.AsyncExecution;
import com.roboxue.niffler.execution.ExecutionLogger;

import javax.annotation.Nullable;

public interface Niffler {
    Iterable<DataFlow<?>> getDataFlows();

    default <T> AsyncExecution<T> asyncRun(Token<T> token, Iterable<DataFlow<?>> extraFlow, ExecutionStateTracker sc, @Nullable ExecutionLogger logger) {
        return LanguageBridge.asyncRun(this, token, extraFlow, sc, logger);
    }
}
