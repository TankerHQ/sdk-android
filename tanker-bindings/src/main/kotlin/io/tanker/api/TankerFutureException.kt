package io.tanker.api

/**
 * Exceptions thrown by TankerFutures to wrap an inner exception
 * This serves to propagate both the stacktrace of the original exception and of the failure point
 * @see TankerFuture
 */
open class TankerFutureException(other: Throwable): Exception("Future is in an error state", other)