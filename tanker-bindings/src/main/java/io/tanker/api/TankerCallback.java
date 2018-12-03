package io.tanker.api;

/**
 * A callback that can be passed to a {@see TankerFuture}
 * The returned value will be wrapped in a new TankerFuture
 * @param <T> The result type of the TankerFuture calling the function
 * @param <U> The type returned to the TankerFuture
 */
@FunctionalInterface
public interface TankerCallback<T, U> {
    U call(T result) throws Exception;
}
