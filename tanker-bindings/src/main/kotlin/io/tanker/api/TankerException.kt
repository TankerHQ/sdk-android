package io.tanker.api

import io.tanker.bindings.TankerError

/**
 * Exceptions returned by the SDK through TankerFutures
 * @see TankerFuture
 */
open class TankerException : Exception {
    val errorCode: ErrorCode

    internal constructor(error: TankerError) : super(error.getErrorMessage()) {
        errorCode = error.getErrorCode()
    }
}