package io.tanker.api

import android.os.Looper
import android.os.NetworkOnMainThreadException
import com.sun.jna.Pointer
import io.tanker.bindings.TankerLib
import java.lang.reflect.Type
import java.util.concurrent.Executors
import android.support.annotation.WorkerThread
import io.tanker.bindings.AsyncLib

class TankerFuture<T>(private var cfuture: Pointer, private var valueType: Type, private var lib: AsyncLib = tankerlib) {
    private sealed class ThenResult {
        data class Object(val result: Any?) : ThenResult()
        data class Error(val error: Throwable) : ThenResult()
    }

    private var callback: AsyncLib.FutureCallback? = null
    private var parent: TankerFuture<*>? = null
    private var thenResult: ThenResult? = null

    companion object {
        // When passing a callback to C, we need to keep ourselves alive until the callback is
        // invoked, or it will crash. This is the list of futures whose callbacks are pending.
        @JvmStatic
        private var lifeSupport: MutableList<TankerFuture<*>> = ArrayList()

        internal var threadPool = Executors.newCachedThreadPool()
        private val tankerlib = TankerLib.create()

        /**
         * Creates a future that completes when all the futures complete
         * The returned future always resolves successfully with the array of futures,
         * which should be checked to know the result of each future.
         */
        @JvmStatic fun allOf(futures: Array<TankerFuture<*>>): TankerFuture<Unit> {
            var firstErr: Throwable? = null
            var acc = TankerFuture<Unit>()
            for (elem in futures)
                acc = acc.thenUnwrap(TankerUnwrapCallback {
                    it.getError()?.let { firstErr = firstErr ?: it }
                    @Suppress("UNCHECKED_CAST") // Discarding results of the future is safe
                    elem as TankerFuture<Unit>
                })
            return acc.then(TankerVoidCallback {
                firstErr?.let { throw it }
                it.getError()?.let { throw it }
            })
        }
    }

    private class ThenResultType private constructor()

    /**
     * Create a ready future returning Unit
     */
    constructor(lib: AsyncLib = tankerlib) : this(Pointer(0), Unit.javaClass) {
        val prom = lib.tanker_promise_create()
        cfuture = lib.tanker_promise_get_future(prom)
        lib.tanker_promise_set_value(prom, Pointer(0))
        lib.tanker_promise_destroy(prom)
    }

    /**
     * Creates the futures returned by then()
     */
    private constructor(parent: TankerFuture<*>, cfuture: Pointer)
            : this(cfuture, ThenResultType::class.java) {
        this.parent = parent
    }

    @Suppress("ProtectedInFinal", "Unused") protected fun finalize() {
        lib.tanker_future_destroy(cfuture)
    }

    /**
     * Returns the Future's stored error, if any
     */
    fun getError(): Throwable? {
        if (valueType == ThenResultType::class.java)
            return (thenResult as? ThenResult.Error)?.error

        if ((lib.tanker_future_has_error(cfuture) and 0xff) == 0)
            return null
        val tankerError = lib.tanker_future_get_error(cfuture)
        return TankerException(tankerError)
    }

    /**
     * Returns whether the future is ready
     */
    fun isReady(): Boolean {
        return (lib.tanker_future_is_ready(cfuture) and 0xff) != 0
    }

    /**
     * Blocks until the future is ready without returning a result
     */
    fun block() {
        lib.tanker_future_wait(cfuture)
    }

    /**
     * Blocks until the future is ready and returns its result
     * Throws if there is an error
     */
    @Throws(TankerFutureException::class)
    @WorkerThread
    fun get(): T {
        val isAndroid = System.getProperty("java.specification.vendor") == "The Android Project"
        if (isAndroid && Looper.getMainLooper().thread == Thread.currentThread())
            throw NetworkOnMainThreadException()

        lib.tanker_future_wait(cfuture)
        getError()?.let {
            throw TankerFutureException(it)
        }

        @Suppress("UNCHECKED_CAST") // As T cast is checked by when statement
        return when (valueType) {
            ThenResultType::class.java -> (thenResult as ThenResult.Object).result
            Pointer::class.java -> lib.tanker_future_get_voidptr(cfuture)
            Boolean::class.java -> Pointer.nativeValue(lib.tanker_future_get_voidptr(cfuture)) != 0L
            Int::class.java -> Pointer.nativeValue(lib.tanker_future_get_voidptr(cfuture)).toInt()
            Unit::class.java -> Unit
            else -> throw RuntimeException("Tried to get() a TankerFuture of unknown type")
        } as T
    }

    /**
     * Executes {@code userCallback} when the future is ready
     *
     * @param userCallback A callback taking "this" and returning nothing
     * @return A future that resolves when @{code userCallback} returns
     */
    fun then(userCallback: TankerVoidCallback<TankerFuture<T>>): TankerFuture<Unit> {
        return then(TankerCallback { userCallback.call(it) })
    }

    /**
     * Executes {@code userCallback} when the future is ready
     *
     * @param userCallback A callback taking "this" and returning a value
     * @return A future whose result is the return value of @{code userCallback}
     */
    fun <U> then(userCallback: TankerCallback<TankerFuture<T>, U>): TankerFuture<U> {
        if (this.callback != null)
            throw RuntimeException("Cannot call then() multiple times on the same future")

        lifeSupport.add(this)

        val thenAsyncPromise = lib.tanker_promise_create()
        val thenAsyncFuture = lib.tanker_promise_get_future(thenAsyncPromise)
        val resultFuture = TankerFuture<U>(this, thenAsyncFuture)

        callback = object : AsyncLib.FutureCallback {
            override fun callback(userArg: Pointer?): Pointer {
                threadPool.execute({
                    try {
                        resultFuture.thenResult = ThenResult.Object(userCallback.call(this@TankerFuture))
                    } catch (e: Throwable) {
                        resultFuture.thenResult = ThenResult.Error(e)
                    }
                    lifeSupport.remove(this@TankerFuture)
                    lib.tanker_promise_set_value(thenAsyncPromise, Pointer(0))
                })
                return Pointer(0)
            }
        }
        val thenCFuture = lib.tanker_future_then(cfuture, callback as AsyncLib.FutureCallback, Pointer(0))
        lib.tanker_future_destroy(thenCFuture)
        return resultFuture
    }

    /**
     * Executes {@code userCallback} when the future is ready, then unwraps the result
     * Warning: The future returned by the callback may NOT safely be used after this call
     *
     * @param userCallback A callback taking "this" and returning a future
     * @return A future whose result is the same as the future returned by @{code userCallback}
     */
    fun <U> thenUnwrap(userCallback: TankerUnwrapCallback<TankerFuture<T>, U>): TankerFuture<U> {
        if (this.callback != null)
            throw RuntimeException("Cannot call then() multiple times on the same future")

        lifeSupport.add(this)

        val thenUnwrapPromise = lib.tanker_promise_create()
        val thenUnwrapFuture = lib.tanker_promise_get_future(thenUnwrapPromise)
        val resultFuture = TankerFuture<U>(this, thenUnwrapFuture)

        callback = object : AsyncLib.FutureCallback {
            override fun callback(userArg: Pointer?): Pointer {
                threadPool.execute({
                    try {
                        val wrappedFut = userCallback.call(this@TankerFuture)
                        if (wrappedFut.callback != null)
                            throw RuntimeException("Cannot call then() multiple times on the same future")

                        lifeSupport.add(wrappedFut)

                        wrappedFut.callback = object : AsyncLib.FutureCallback {
                            override fun callback(userArg: Pointer?): Pointer {
                                try {
                                    lifeSupport.remove(wrappedFut)
                                    resultFuture.thenResult = ThenResult.Object(wrappedFut.get())
                                } catch (e: Throwable) {
                                    resultFuture.thenResult = ThenResult.Error(e)
                                }
                                lib.tanker_promise_set_value(thenUnwrapPromise, Pointer(0))
                                lib.tanker_promise_destroy(thenUnwrapPromise)
                                return Pointer(0)
                            }
                        }

                        resultFuture.cfuture = lib.tanker_future_then(wrappedFut.cfuture, wrappedFut.callback!!, Pointer(0))
                    } catch (e: Throwable) {
                        resultFuture.thenResult = ThenResult.Error(e)
                        lib.tanker_promise_set_value(thenUnwrapPromise, Pointer(0))
                        lib.tanker_promise_destroy(thenUnwrapPromise)
                    }
                    lifeSupport.remove(this@TankerFuture)
                })
                return Pointer(0)
            }
        }

        val thenCFuture = lib.tanker_future_then(cfuture, callback as AsyncLib.FutureCallback, Pointer(0))
        lib.tanker_future_destroy(thenCFuture)
        return resultFuture
    }

    /**
     * Executes {@code userCallback} if and when the future is ready and successful
     * If the future has an error, the callback is not executed and the error is forwarded
     *
     * @param userCallback A callback taking "this" and returning nothing
     * @return A future that resolves on error, or when @{code userCallback} returns
     */
    fun andThen(userCallback: TankerVoidCallback<T>): TankerFuture<Unit> {
        return andThen(TankerCallback { userCallback.call(it) })
    }

    /**
     * Executes {@code userCallback} if and when the future is ready and successful
     * If the future has an error, the callback is not executed and the error is forwarded
     *
     * @param userCallback A callback taking "this" and returning a value
     * @return A future whose result is this's error, or the return value of @{code userCallback}
     */
    fun <U> andThen(userCallback: TankerCallback<T, U>): TankerFuture<U> {
        return then(TankerCallback {
            it.getError()?.let { throw it }
            userCallback.call(it.get())
        })
    }

    /**
     * Executes {@code userCallback} if and when the future is ready and successful, and unwraps the result.
     * If the future has an error, the callback is not executed and the error is forwarded.
     * Warning: The future returned by the callback may NOT safely be used after this call
     *
     * @param userCallback A callback taking "this" and returning a future
     * @return A future whose result is this's error, or the same as the future returned by @{code userCallback}
     */
    fun <U> andThenUnwrap(userCallback: TankerUnwrapCallback<T, U>): TankerFuture<U> {
        return thenUnwrap(TankerUnwrapCallback {
            it.getError()?.run {
                it.transmute<U>(valueType)
            } ?: run {
                userCallback.call(it.get())
            }
        })
    }

    /**
     * Executes {@code userCallback} if the future resolves with an error
     * If the future is successful, the callback is not executed and the result is forwarded
     *
     * @param userCallback A callback taking "this" and returning a value
     * @return A future whose result is this's result, or the result of @{code userCallback}
     */
    fun orElse(userCallback: TankerCallback<Throwable, T>): TankerFuture<T> {
        return then(TankerCallback {
            it.getError()?.let { return@TankerCallback userCallback.call(it) }
            it.get()
        })
    }

    // Please note that this function is incredibly unsafe/useful.
    // To avoid explosions, it should only be called on futures that have resolved to an error.
    @Suppress("UNCHECKED_CAST") // Internal functions may throw if badly misused, that's ok
    internal fun<U> transmute(newValueType: Type): TankerFuture<U> {
        assert(getError() != null)
        valueType = newValueType
        return this as TankerFuture<U>
    }
}
