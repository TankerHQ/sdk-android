package io.tanker.api;

/**
 * A callback that can be passed to Tanker.connectValidationHandler
 */
@FunctionalInterface
public interface TankerValidationHandler {
    void call(String validationCode) throws Exception;
}