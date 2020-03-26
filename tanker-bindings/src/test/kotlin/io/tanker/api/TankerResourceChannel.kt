package io.tanker.api

import android.support.annotation.RequiresApi
import io.kotlintest.*
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.nio.channels.ReadPendingException
import java.util.concurrent.FutureTask

class BlockingChannel : TankerAsynchronousByteChannel {
    private var isClosed = false

    override fun <A> read(dst: ByteBuffer, attachment: A, handler: TankerCompletionHandler<Int, in A>) {
    }

    override fun isOpen(): Boolean {
        return !isClosed
    }

    override fun close() {
        isClosed = true
    }
}

class DummyChannel : TankerAsynchronousByteChannel {
    val clearBuffer = ByteBuffer.allocate(1024 * 1024 * 2)!!
    private var isClosed = false

    override fun <A> read(dst: ByteBuffer, attachment: A, handler: TankerCompletionHandler<Int, in A>) {
        try {
            if (dst.remaining() == 0) {
                handler.completed(0, attachment)
            } else if (clearBuffer.remaining() == 0) {
                handler.completed(-1, attachment)
            } else {
                val clearArray = clearBuffer.array()
                val currentPos = clearBuffer.arrayOffset()
                val finalLength = minOf(dst.remaining(), clearBuffer.remaining())
                dst.put(clearArray, currentPos, finalLength)
                clearBuffer.position(clearBuffer.position() + finalLength)
                handler.completed(finalLength, attachment)
            }
        } catch (e: Throwable) {
            handler.failed(e, attachment)
        }
    }

    override fun isOpen(): Boolean {
        return !isClosed
    }

    override fun close() {
        isClosed = true
    }
}

@RequiresApi(26)
class API26StreamChannelTestHelper(tanker: Tanker, chan: TankerAsynchronousByteChannel, decrypt: Boolean) {
    val dummyChannel = DummyChannel()
    val clearChannel = TankerChannels.toAsynchronousByteChannel(dummyChannel)
    var err: Throwable? = null
    var nbRead = 0
    var outputChannel: AsynchronousByteChannel
    val decryptedBuffer = ByteBuffer.allocate(1024 * 1024 * 2)!!
    val fut = FutureTask {}

    fun callback(): CompletionHandler<Int, Unit> {
        return object : CompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit) {
                if (result == -1) {
                    fut.run()
                } else {
                    nbRead += result
                    outputChannel.read(decryptedBuffer, Unit, this)
                }
            }

            override fun failed(exc: Throwable, attachment: Unit) {
                err = exc
                fut.run()
            }
        }
    }

    init {
        val encryptChannel = tanker.encrypt(chan).get()
        outputChannel =
                if (decrypt)
                    TankerChannels.toAsynchronousByteChannel(tanker.decrypt(encryptChannel).get())
                else
                    TankerChannels.toAsynchronousByteChannel(encryptChannel)
    }
}

@RequiresApi(26)
class API26StreamChannelTests : TankerSpec() {
    lateinit var tanker: Tanker

    override fun beforeTest(testCase: TestCase) {
        tanker = Tanker(options.setWritablePath(createTmpDir().toString()))
        val st = tanker.start(tc.createIdentity()).get()
        st shouldBe Status.IDENTITY_REGISTRATION_NEEDED
        tanker.registerIdentity(PassphraseVerification("")).get()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        super.afterTest(testCase, result)
        tanker.stop().get()
    }

    init {
        "Reading asynchronously" {
            val helper = API26StreamChannelTestHelper(tanker, DummyChannel(), true)
            helper.outputChannel.read(helper.decryptedBuffer, Unit, helper.callback())
            helper.fut.get()
            helper.err shouldBe null
            helper.nbRead shouldBe helper.decryptedBuffer.capacity()
            helper.dummyChannel.clearBuffer.position(0)
            helper.decryptedBuffer shouldBe helper.dummyChannel.clearBuffer
        }

        "Reading a closed channel throws" {
            val helper = API26StreamChannelTestHelper(tanker, BlockingChannel(), false)
            helper.outputChannel.read(helper.decryptedBuffer, Unit, helper.callback())
            helper.outputChannel.close()
            helper.fut.get()
            helper.err shouldNotBe null
            (helper.err is ClosedChannelException) shouldBe true
        }

        "Attempting two read operations simultaneously throws" {
            val helper = API26StreamChannelTestHelper(tanker, BlockingChannel(), false)
            val secondBuffer = ByteBuffer.allocate(helper.decryptedBuffer.capacity())
            helper.outputChannel.read(helper.decryptedBuffer, Unit, helper.callback())
            shouldThrow<ReadPendingException> { helper.outputChannel.read(secondBuffer, Unit, helper.callback()) }
        }
    }
}
