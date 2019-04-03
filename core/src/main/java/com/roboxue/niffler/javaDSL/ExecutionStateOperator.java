package com.roboxue.niffler.javaDSL;

import com.roboxue.niffler.ExecutionStateLike;

/**
 * @author robert.xue
 * @since 2019-04-01
 */
@FunctionalInterface
public interface ExecutionStateOperator<Result> {

    Result apply(ExecutionStateLike input1) throws Exception;
}
