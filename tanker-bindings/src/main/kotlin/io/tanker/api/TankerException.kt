package io.tanker.api

import io.tanker.bindings.TankerError

/**
 * Exceptions returned by the SDK through TankerFutures
 * @see TankerFuture
 */
open class TankerException : Exception {
    val errorCode: ErrorCode

    internal constructor(error: ErrorCode, message: String) : super(message) {
        errorCode = error
    }
}