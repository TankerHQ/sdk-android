package io.tanker.bindings

import com.sun.jna.*

typealias FuturePointer = Pointer
typealias ExpectedPointer = FuturePointer
typealias PromisePointer = Pointer

@Suppress("FunctionName")
interface AsyncLib : Library {
    interface FutureCallback : Callback {
        fun callback(userArg: Pointer?): Pointer
    }

    fun tanker_future_is_ready(future: FuturePointer): DangerousNativeBool
    fun tanker_future_wait(future: FuturePointer): Void
    fun tanker_future_then(future: FuturePointer, callback: FutureCallback, userArg: Pointer): FuturePointer
    fun tanker_future_has_error(future: FuturePointer): DangerousNativeBool
    fun tanker_future_get_error(future: FuturePointer): TankerError
    fun tanker_future_destroy(future: FuturePointer): Void
    fun tanker_future_get_voidptr(future: FuturePointer): Pointer

    fun tanker_promise_create(): PromisePointer
    fun tanker_promise_destroy(promise: PromisePointer): Void
    fun tanker_promise_get_future(promise: PromisePointer): FuturePointer
    fun tanker_promise_set_value(promise: PromisePointer, value: Pointer): Void
}

