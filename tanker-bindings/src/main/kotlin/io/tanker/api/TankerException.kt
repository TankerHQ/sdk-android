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

    constructor(message: String, other: TankerException) : super(message, other){
        errorCode = other.errorCode
    }

    constructor(message: String, errorCode: ErrorCode, other: Throwable) : super(message, other){
        this.errorCode = errorCode
    }
}