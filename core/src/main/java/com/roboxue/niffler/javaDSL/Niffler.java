package com.roboxue.niffler.javaDSL;

import com.roboxue.niffler.DataFlow;

public interface Niffler {
    Iterable<DataFlow<?>> getDataFlows();
}
