package io.tanker.api;

/**
 * A callback that can be passed to Tanker.connectSessionClosedHandler
 */
@FunctionalInterface
public interface TankerSessionClosedHandler {
    void call() throws Exception;
}