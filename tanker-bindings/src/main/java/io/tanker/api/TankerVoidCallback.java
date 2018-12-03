package io.tanker.api;

/**
 * A callback that can be passed to a {@see TankerFuture}
 * @param <T> The result type of the TankerFuture calling the function
 */
@FunctionalInterface
public interface TankerVoidCallback<T> {
    void call(T result) throws Exception;
}
