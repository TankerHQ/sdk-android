package io.tanker.api

import com.sun.jna.Pointer
import io.tanker.api.errors.InvalidArgument
import io.tanker.bindings.FuturePointer
import io.tanker.bindings.PromisePointer
import io.tanker.bindings.TankerLib
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class FutureTests {
    private val lib = TankerLib.create()
    private lateinit var tankerPromise: PromisePointer
    private lateinit var tankerFuture: FuturePointer

    @Before
    fun beforeTest() {
        tankerPromise = lib.tanker_promise_create()
        tankerFuture = lib.tanker_promise_get_future(tankerPromise)
        lib.tanker_promise_set_value(tankerPromise, Pointer(0))
    }

    @After
    fun afterTest() {
        lib.tanker_promise_destroy(tankerPromise)
    }

    @Test
    fun constructing_a_TankerFuture_should_not_fail() {
        TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
    }

    @Test
    fun can_get_a_ready_future() {
        val future = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        assertThat(future.get()).isEqualTo(Unit)
    }

    @Test
    fun can_create_an_get_a_new_ready_future() {
        assertThat(TankerFuture<Unit>().then<Int>(TankerCallback { 2010 }).get()).isEqualTo(2010)
    }

    @Test
    fun then_can_return_an_Int() {
        val future = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val newFut = future.then<Int>(TankerCallback { 42 })
        System.gc() // Make sure we got our lifetimes right
        assertThat(newFut.get()).isEqualTo(42)
    }

    @Test
    fun then_can_return_a_Long() {
        val future = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val newFut = future.then<Long>(TankerCallback { 42L })
        System.gc() // Make sure we got our lifetimes right
        assertThat(newFut.get()).isEqualTo(42L)
    }

    @Test
    fun then_can_return_a_ByteArray() {
        val data = ByteArray(16) { idx -> idx.toByte() }
        val future = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val newFut = future.then<ByteArray>(TankerCallback { data })
        System.gc() // Make sure we got our lifetimes right
        val res: ByteArray = newFut.get()
        assertThat(data.contentEquals(res)).isEqualTo(true)
    }

    @Test
    fun then_can_return_an_error() {
        val future = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val exception = RuntimeException("Error")
        val newFut = future.then<ByteArray>(TankerCallback { throw exception })
        System.gc() // Make sure we got our lifetimes right
        val e = shouldThrow<TankerFutureException> { newFut.get() }
        assertThat(e).hasCause(exception)
    }

    @Test
    fun can_chain_then_calls_with_different_types() {
        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val result = fut.then<Double>(TankerCallback {
            170.37
        }).then<Int>(TankerCallback { readyFuture ->
            ((readyFuture.get()) / 10).toInt()
        }).get()
        assertThat(result).isEqualTo(17)
    }

    @Test
    fun future_unwrapping_works_with_then() {
        val str = "this is a very long string"
        val wrappedCPromise = lib.tanker_promise_create()
        val wrappedCFuture = lib.tanker_promise_get_future(wrappedCPromise)
        val wrappedFuture = TankerFuture<Unit>(wrappedCFuture, Unit::class.java, keepAlive = null)
            .then<String>(TankerCallback { str })

        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val result = fut.thenUnwrap<String>(TankerUnwrapCallback {
            Thread.sleep(25) // If there's any race, we want to lose it so the test fails
            lib.tanker_promise_set_value(wrappedCPromise, Pointer(0))
            Thread.sleep(25)
            wrappedFuture
        }).get()
        assertThat(result).isEqualTo(str)
    }

    @Test
    fun can_chain_future_unwrapping_with_then() {
        val str = "This is a very long string"
        val wrappedCPromise = lib.tanker_promise_create()
        val wrappedCFuture = lib.tanker_promise_get_future(wrappedCPromise)
        val wrappedFuture = TankerFuture<Unit>(wrappedCFuture, Unit::class.java, keepAlive = null)
            .then<String>(TankerCallback { str })

        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val result = fut.thenUnwrap<String>(TankerUnwrapCallback {
            Thread.sleep(25) // If there's any race, we want to lose it so the test fails
            lib.tanker_promise_set_value(wrappedCPromise, Pointer(0))
            Thread.sleep(25)
            wrappedFuture
        }).then<String>(TankerCallback {
            System.gc()
            it.get() + it.get()
        }).get()
        assertThat(result).isEqualTo(str + str)
    }

    @Test
    fun thenUnwrap_catches_errors_correctly() {
        val exception = RuntimeException("Error")
        val failing = TankerFuture<Unit>().then<Unit>(TankerCallback { throw exception })
        val fut = TankerFuture<Unit>().thenUnwrap<Unit>(TankerUnwrapCallback { failing })
        val e = shouldThrow<TankerFutureException> { fut.get() }
        assertThat(e.cause!!.cause!!).isSameAs(exception)
    }

    @Test
    fun a_get_in_a_then_does_not_hang() {
        val innerCPromise = lib.tanker_promise_create()
        val innerCFuture = lib.tanker_promise_get_future(innerCPromise)
        val innerFuture = TankerFuture<Unit>(innerCFuture, Unit::class.java, keepAlive = null)
        lib.tanker_promise_set_value(innerCPromise, Pointer(0))
        innerFuture.get()

        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val result = fut.then<Int>(TankerCallback {
            1031 + innerFuture.then<Int>(TankerCallback { 8 }).get()
        })
        while (!result.isReady())
            Thread.sleep(100)
        assertThat(result.get()).isEqualTo(1039)
    }

    @Test
    fun a_get_in_an_unwrapping_then_does_not_hang() {
        val innerCPromise = lib.tanker_promise_create()
        val innerCFuture = lib.tanker_promise_get_future(innerCPromise)
        val innerFuture = TankerFuture<Unit>(innerCFuture, Unit::class.java, keepAlive = null)
        lib.tanker_promise_set_value(innerCPromise, Pointer(0))
        innerFuture.get()

        val blockingCPromise = lib.tanker_promise_create()
        val blockingCFuture = lib.tanker_promise_get_future(blockingCPromise)
        val blockingFuture = TankerFuture<Unit>(blockingCFuture, Unit::class.java, keepAlive = null)
        lib.tanker_promise_set_value(blockingCPromise, Pointer(0))

        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val result = fut.thenUnwrap<Int>(TankerUnwrapCallback {
            val value = blockingFuture.then<Int>(TankerCallback { 1031 }).get()
            innerFuture.then<Int>(TankerCallback {
                value
            })
        })
        while (!result.isReady())
            Thread.sleep(100)
        assertThat(result.get()).isEqualTo(1031)
    }

    @Test
    fun andThen_can_return_a_value() {
        val future = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val newFut = future.andThen<Int>(TankerCallback { 42 })
        System.gc() // Make sure we got our lifetimes right
        assertThat(newFut.get()).isEqualTo(42)
    }

    @Test
    fun andThen_can_return_an_error() {
        val future = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val exception = RuntimeException("Error")
        val newFut = future.andThen<ByteArray>(TankerCallback { throw exception })
        System.gc() // Make sure we got our lifetimes right
        val e = shouldThrow<TankerFutureException> { newFut.get() }
        assertThat(e).hasCause(exception)
    }

    @Test
    fun can_chain_andThen_calls_with_different_types() {
        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val result = fut.andThen<Double>(TankerCallback {
            170.37
        }).andThen<Int>(TankerCallback { result ->
            (result / 10).toInt()
        }).get()
        assertThat(result).isEqualTo(17)
    }

    @Test
    fun future_unwrapping_works_with_andThen() {
        val str = "this a very long string"
        val wrappedCPromise = lib.tanker_promise_create()
        val wrappedCFuture = lib.tanker_promise_get_future(wrappedCPromise)
        val wrappedFuture = TankerFuture<Unit>(wrappedCFuture, Unit::class.java, keepAlive = null)
            .andThen<String>(TankerCallback { str })

        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val result = fut.andThenUnwrap<String>(TankerUnwrapCallback {
            Thread.sleep(25) // If there's any race, we want to lose it so the test fails
            lib.tanker_promise_set_value(wrappedCPromise, Pointer(0))
            Thread.sleep(25)
            wrappedFuture
        }).get()
        assertThat(result).isEqualTo(str)
    }

    @Test
    fun can_chain_future_unwrapping_with_andThen() {
        val str = "this is a very long string"
        val wrappedCPromise = lib.tanker_promise_create()
        val wrappedCFuture = lib.tanker_promise_get_future(wrappedCPromise)
        val wrappedFuture = TankerFuture<Unit>(wrappedCFuture, Unit::class.java, keepAlive = null)
            .andThen<String>(TankerCallback { str })

        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
        val result = fut.andThenUnwrap<String>(TankerUnwrapCallback {
            Thread.sleep(25) // If there's any race, we want to lose it so the test fails
            lib.tanker_promise_set_value(wrappedCPromise, Pointer(0))
            Thread.sleep(25)
            wrappedFuture
        }).andThen<String>(TankerCallback {
            System.gc()
            it + it
        }).get()
        assertThat(result).isEqualTo(str + str)
    }

    @Test
    fun andThen_stops_executing_a_chain_after_an_error() {
        val except = RuntimeException("Error")
        val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java, keepAlive = null)
            .andThen<Double>(TankerCallback {
                throw except
            }).andThen<String>(TankerCallback {
                "This should never be returned"
            })
        fut.block()
        assertThat(fut.getError()).isEqualTo(except)
    }

    @Test
    fun orElse_can_forward_the_original_value() {
        val fut = TankerFuture<Unit>()
            .then<Int>(TankerCallback { 25 })
            .orElse(TankerCallback { -1 })
        System.gc() // Make sure we got our lifetimes right
        assertThat(fut.get()).isEqualTo(25)
    }

    @Test
    fun orElse_calls_the_callback_on_error() {
        val fut = TankerFuture<Unit>()
            .then<Int>(TankerCallback { throw RuntimeException("Error") })
            .orElse(TankerCallback { -1 })
        System.gc() // Make sure we got our lifetimes right
        assertThat(fut.get()).isEqualTo(-1)
    }

    @Test
    fun can_use_allOf_on_two_futures() {
        val fut1 = TankerFuture<Unit>().then<Int>(TankerCallback { 1 })
        val fut2 = TankerFuture<Unit>().then<Int>(TankerCallback { 2 })
        TankerFuture.allOf(arrayOf(fut1, fut2)).get()
        assertThat(fut1.isReady()).isEqualTo(true)
        assertThat(fut2.isReady()).isEqualTo(true)
        assertThat(fut1.get()).isEqualTo(1)
        assertThat(fut2.get()).isEqualTo(2)
    }

    @Test
    fun allOf_returns_an_error_if_the_first_future_errors_out() {
        val exception = RuntimeException("Error")
        val fut1 = TankerFuture<Unit>().then<Int>(TankerCallback { throw exception })
        val fut2 = TankerFuture<Unit>().then<Int>(TankerCallback { Thread.sleep(100); 2 })
        val e = shouldThrow<TankerFutureException> { TankerFuture.allOf(arrayOf(fut1, fut2)).get() }
        assertThat(e.cause!!.cause!!).isSameAs(exception)
        assertThat(fut1.isReady()).isEqualTo(true)
        assertThat(fut2.isReady()).isEqualTo(true)
    }

    @Test
    fun allOf_returns_an_error_if_the_second_future_errors_out() {
        val exception = RuntimeException("Error")
        val fut1 = TankerFuture<Unit>().then<Int>(TankerCallback { Thread.sleep(100); 1 })
        val fut2 = TankerFuture<Unit>().then<Int>(TankerCallback { throw exception })
        val e = shouldThrow<TankerFutureException> { TankerFuture.allOf(arrayOf(fut1, fut2)).get() }
        assertThat(e.cause!!.cause!!).isSameAs(exception)
        assertThat(fut1.isReady()).isEqualTo(true)
        assertThat(fut2.isReady()).isEqualTo(true)
    }

    @Test
    fun allOf_returns_the_first_error_if_both_futures_errors_out() {
        val fut1 =
            TankerFuture<Unit>().then<Int>(TankerCallback { throw RuntimeException("Error") })
        val fut2 =
            TankerFuture<Unit>().then<Int>(TankerCallback { throw RuntimeException("Other Error") })
        val except =
            shouldThrow<TankerFutureException> { TankerFuture.allOf(arrayOf(fut1, fut2)).get() }
        assertThat(except.cause!!.cause!!.message).isEqualTo("Error")
    }
}
