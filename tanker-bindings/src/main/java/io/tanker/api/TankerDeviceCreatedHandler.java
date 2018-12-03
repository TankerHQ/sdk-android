package io.tanker.api;

/**
 * A callback that can be passed to Tanker.connectDeviceCreatedHandler
 */
@FunctionalInterface
public interface TankerDeviceCreatedHandler {
    void call() throws Exception;
}
