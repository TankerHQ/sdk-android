package io.tanker.api

import com.sun.jna.Pointer
import io.kotlintest.*
import io.kotlintest.specs.StringSpec
import io.tanker.bindings.FuturePointer
import io.tanker.bindings.PromisePointer
import io.tanker.bindings.TankerLib

class FutureTests : StringSpec() {
    private val lib = TankerLib.create()
    private lateinit var tankerPromise: PromisePointer
    private lateinit var tankerFuture: FuturePointer
    override val defaultTestCaseConfig = TestCaseConfig(timeout = 30.seconds)

    override fun beforeTest(testCase: TestCase) {
        tankerPromise = lib.tanker_promise_create()
        tankerFuture = lib.tanker_promise_get_future(tankerPromise)
        lib.tanker_promise_set_value(tankerPromise, Pointer(0))
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        lib.tanker_promise_destroy(tankerPromise)
    }
    init {
        "Constructing a TankerFuture should not fail" {
            TankerFuture<Unit>(tankerFuture, Unit::class.java)
        }

        "Can get() a ready future" {
            val future = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            future.get() shouldBe Unit
        }

        "Can create an get() a new ready future" {
            TankerFuture<Unit>().then<Int>(TankerCallback { 2010 }).get() shouldBe 2010
        }

        "then() can return an Int" {
            val future = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val newFut = future.then<Int>(TankerCallback { 42 })
            System.gc() // Make sure we got our lifetimes right
            newFut.get() shouldBe 42
        }

        "then() can return a Long" {
            val future = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val newFut = future.then<Long>(TankerCallback { 42L })
            System.gc() // Make sure we got our lifetimes right
            newFut.get() shouldBe 42L
        }

        "then() can return a ByteArray" {
            val data = ByteArray(16) { idx -> idx.toByte() }
            val future = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val newFut = future.then<ByteArray>(TankerCallback { data })
            System.gc() // Make sure we got our lifetimes right
            val res: ByteArray = newFut.get()
            data.contentEquals(res) shouldBe true
        }

        "then() can return an error" {
            val future = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val newFut = future.then<ByteArray>(TankerCallback { throw RuntimeException("Error") })
            System.gc() // Make sure we got our lifetimes right
            shouldThrow<TankerFutureException> { newFut.get() }
        }

        "Can chain then() calls with different types" {
            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val result = fut.then<Double>(TankerCallback {
                170.37
            }).then<Int>(TankerCallback { readyFuture ->
                ((readyFuture.get()) / 10).toInt()
            }).get()
            result shouldBe 17
        }

        "Future unwrapping works with then()" {
            val str = "this is a very long string"
            val wrappedCPromise = lib.tanker_promise_create()
            val wrappedCFuture = lib.tanker_promise_get_future(wrappedCPromise)
            val wrappedFuture = TankerFuture<Unit>(wrappedCFuture, Unit::class.java)
                    .then<String>(TankerCallback { str })

            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val result = fut.thenUnwrap<String>(TankerUnwrapCallback {
                Thread.sleep(25) // If there's any race, we want to lose it so the test fails
                lib.tanker_promise_set_value(wrappedCPromise, Pointer(0))
                Thread.sleep(25)
                wrappedFuture
            }).get()
            result shouldBe str
        }

        "Can chain future unwrapping with then()" {
            val str = "This is a very long string"
            val wrappedCPromise = lib.tanker_promise_create()
            val wrappedCFuture = lib.tanker_promise_get_future(wrappedCPromise)
            val wrappedFuture = TankerFuture<Unit>(wrappedCFuture, Unit::class.java)
                    .then<String>(TankerCallback { str })

            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val result = fut.thenUnwrap<String>(TankerUnwrapCallback {
                Thread.sleep(25) // If there's any race, we want to lose it so the test fails
                lib.tanker_promise_set_value(wrappedCPromise, Pointer(0))
                Thread.sleep(25)
                wrappedFuture
            }).then<String>(TankerCallback {
                System.gc()
                it.get()+it.get()
            }).get()
            result shouldBe str+str
        }

        "thenUnwrap() catches errors correctly" {
            val failing = TankerFuture<Unit>().then<Unit>(TankerCallback { throw RuntimeException("Error") })
            val fut = TankerFuture<Unit>().thenUnwrap<Unit>(TankerUnwrapCallback { failing })
            shouldThrow<TankerFutureException> { fut.get() }
        }

        "A get() in a then() does not hang" {
            val testThread = Thread.currentThread()
            val watchdogThread = Thread {
                Thread.sleep(3000)
                testThread.interrupt()
            }
            watchdogThread.start()

            val innerCPromise = lib.tanker_promise_create()
            val innerCFuture = lib.tanker_promise_get_future(innerCPromise)
            val innerFuture = TankerFuture<Unit>(innerCFuture, Unit::class.java)
            lib.tanker_promise_set_value(innerCPromise, Pointer(0))
            innerFuture.get()

            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val result = fut.then<Int>(TankerCallback {
                1031 + innerFuture.then<Int>(TankerCallback { 8 }).get()
            })
            while (!result.isReady())
                Thread.sleep(100)
            result.get() shouldBe 1039
        }

        "A get() in an unwrapping then() does not hang" {
            val testThread = Thread.currentThread()
            val watchdogThread = Thread {
                Thread.sleep(3000)
                testThread.interrupt()
            }
            watchdogThread.start()

            val innerCPromise = lib.tanker_promise_create()
            val innerCFuture = lib.tanker_promise_get_future(innerCPromise)
            val innerFuture = TankerFuture<Unit>(innerCFuture, Unit::class.java)
            lib.tanker_promise_set_value(innerCPromise, Pointer(0))
            innerFuture.get()

            val blockingCPromise = lib.tanker_promise_create()
            val blockingCFuture = lib.tanker_promise_get_future(blockingCPromise)
            val blockingFuture = TankerFuture<Unit>(blockingCFuture, Unit::class.java)
            lib.tanker_promise_set_value(blockingCPromise, Pointer(0))

            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val result = fut.thenUnwrap<Int>(TankerUnwrapCallback {
                val value = blockingFuture.then<Int>(TankerCallback { 1031 }).get()
                innerFuture.then<Int>(TankerCallback {
                    value
                })
            })
            while (!result.isReady())
                Thread.sleep(100)
            result.get() shouldBe 1031
        }

        "andThen() can return a value" {
            val future = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val newFut = future.andThen<Int>(TankerCallback { 42 })
            System.gc() // Make sure we got our lifetimes right
            newFut.get() shouldBe 42
        }

        "andThen() can return an error" {
            val future = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val newFut = future.andThen<ByteArray>(TankerCallback { throw RuntimeException("Error") })
            System.gc() // Make sure we got our lifetimes right
            shouldThrow<TankerFutureException> { newFut.get() }
        }

        "Can chain andThen() calls with different types" {
            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val result = fut.andThen<Double>(TankerCallback {
                170.37
            }).andThen<Int>(TankerCallback { result ->
                (result / 10).toInt()
            }).get()
            result shouldBe 17
        }

        "Future unwrapping works with andThen()" {
            val str = "this a very long string"
            val wrappedCPromise = lib.tanker_promise_create()
            val wrappedCFuture = lib.tanker_promise_get_future(wrappedCPromise)
            val wrappedFuture = TankerFuture<Unit>(wrappedCFuture, Unit::class.java)
                    .andThen<String>(TankerCallback { str })

            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val result = fut.andThenUnwrap<String>(TankerUnwrapCallback {
                Thread.sleep(25) // If there's any race, we want to lose it so the test fails
                lib.tanker_promise_set_value(wrappedCPromise, Pointer(0))
                Thread.sleep(25)
                wrappedFuture
            }).get()
            result shouldBe str
        }

        "Can chain future unwrapping with andThen()" {
            val str = "this is a very long string"
            val wrappedCPromise = lib.tanker_promise_create()
            val wrappedCFuture = lib.tanker_promise_get_future(wrappedCPromise)
            val wrappedFuture = TankerFuture<Unit>(wrappedCFuture, Unit::class.java)
                    .andThen<String>(TankerCallback { str })

            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            val result = fut.andThenUnwrap<String>(TankerUnwrapCallback {
                Thread.sleep(25) // If there's any race, we want to lose it so the test fails
                lib.tanker_promise_set_value(wrappedCPromise, Pointer(0))
                Thread.sleep(25)
                wrappedFuture
            }).andThen<String>(TankerCallback {
                System.gc()
                it+it
            }).get()
            result shouldBe str+str
        }

        "andThen() stops executing a chain after an error" {
            val except = RuntimeException("Error")
            val fut = TankerFuture<Unit>(tankerFuture, Unit::class.java)
            .andThen<Double>(TankerCallback {
                throw except
            }).andThen<String>(TankerCallback {
                "This should never be returned"
            })
            fut.block()
            fut.getError() shouldBe except
        }

        "orElse() can forward the original value" {
            val fut = TankerFuture<Unit>()
                    .then<Int>(TankerCallback { 25 })
                    .orElse(TankerCallback { -1 })
            System.gc() // Make sure we got our lifetimes right
            fut.get() shouldBe 25
        }

        "orElse() calls the callback on error" {
            val fut = TankerFuture<Unit>()
                    .then<Int>(TankerCallback { throw RuntimeException("Error") })
                    .orElse(TankerCallback { -1 })
            System.gc() // Make sure we got our lifetimes right
            fut.get() shouldBe -1
        }

        "Can use allOf on two futures" {
            val fut1 = TankerFuture<Unit>().then<Int>(TankerCallback { 1 })
            val fut2 = TankerFuture<Unit>().then<Int>(TankerCallback { 2 })
            TankerFuture.allOf(arrayOf(fut1, fut2)).get()
            fut1.isReady() shouldBe true
            fut2.isReady() shouldBe true
            fut1.get() shouldBe 1
            fut2.get() shouldBe 2
        }

        "allOf returns an error if the first future errors out" {
            val fut1 = TankerFuture<Unit>().then<Int>(TankerCallback { throw RuntimeException("Error") })
            val fut2 = TankerFuture<Unit>().then<Int>(TankerCallback { Thread.sleep(100); 2 })
            shouldThrow<TankerFutureException> { TankerFuture.allOf(arrayOf(fut1, fut2)).get() }
            fut1.isReady() shouldBe true
            fut2.isReady() shouldBe true
        }

        "allOf returns an error if the second future errors out" {
            val fut1 = TankerFuture<Unit>().then<Int>(TankerCallback { Thread.sleep(100); 1 })
            val fut2 = TankerFuture<Unit>().then<Int>(TankerCallback { throw RuntimeException("Error") })
            shouldThrow<TankerFutureException> { TankerFuture.allOf(arrayOf(fut1, fut2)).get() }
            fut1.isReady() shouldBe true
            fut2.isReady() shouldBe true
        }

        "allOf returns the first error if both futures errors out" {
            val fut1 = TankerFuture<Unit>().then<Int>(TankerCallback { throw RuntimeException("Error") })
            val fut2 = TankerFuture<Unit>().then<Int>(TankerCallback { throw RuntimeException("Other Error") })
            val except = shouldThrow<TankerFutureException> { TankerFuture.allOf(arrayOf(fut1, fut2)).get() }
            except.cause!!.cause!!.message shouldBe "Error"
        }
    }
}

