package io.tanker.api;

/**
 * A callback that can be passed to a {@see TankerFuture}
 * The returned value must be a TankerFuture, its result will be wrapped in a new TankerFuture
 * @param <T> The result type of the TankerFuture calling the function
 * @param <U> The type of the wrapped future returned to the TankerFuture calling the function
 */
@FunctionalInterface
public interface TankerUnwrapCallback<T, U> extends TankerCallback<T, TankerFuture<U>> {
    TankerFuture<U> call(T result) throws Exception;
}
