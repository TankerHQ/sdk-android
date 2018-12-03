package io.tanker.api;

/**
 * A callback that can be passed to Tanker.connectUnlockRequiredHandler
 */
@FunctionalInterface
public interface TankerUnlockRequiredHandler {
    void call() throws Exception;
}