package io.tanker.api

import io.tanker.bindings.TankerError
import io.tanker.bindings.TankerErrorCode

/**
 * Exceptions returned by the SDK through TankerFutures
 * @see TankerFuture
 */
open class TankerException : Exception {
    val errorCode: TankerErrorCode

    internal constructor(error: TankerError) : super(error.getErrorMessage()) {
        errorCode = error.getErrorCode()
    }

    constructor(message: String, other: TankerException) : super(message, other){
        errorCode = other.errorCode
    }

    constructor(message: String, errorCode: TankerErrorCode, other: Throwable) : super(message, other){
        this.errorCode = errorCode
    }
}