package io.tanker.api;

/**
 * A callback that can be passed to Tanker.connectDeviceRevokedHandler
 */
@FunctionalInterface
public interface TankerDeviceRevokedHandler {
    void call() throws Exception;
}
