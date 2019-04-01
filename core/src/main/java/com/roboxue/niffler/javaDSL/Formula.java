package com.roboxue.niffler.javaDSL;

import com.google.common.collect.ImmutableList;
import com.roboxue.niffler.Token;

/**
 * @author robert.xue
 * @since 2019-04-01
 */
public interface Formula<T> {
    ImmutableList<Token<?>> getDependsOn();
    Token<T> getOutlet();
}
