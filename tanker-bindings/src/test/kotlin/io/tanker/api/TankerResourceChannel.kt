package io.tanker.api

import android.support.annotation.RequiresApi
import io.kotlintest.Description
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.nio.channels.ReadPendingException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

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
class API26StreamChannelTestHelper(tanker: Tanker) {
    val dummyChannel = DummyChannel()
    val clearChannel = TankerChannels.toAsynchronousByteChannel(dummyChannel)
    var err: Throwable? = null
    var nbRead = 0
    var decryptor: AsynchronousByteChannel
    val decryptedBuffer = ByteBuffer.allocate(1024 * 1024 * 2)
    val fut = FutureTask {}

    fun callback(): CompletionHandler<Int, Unit> {
        return object : CompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit) {
                if (result == -1) {
                    fut.run()
                } else {
                    nbRead += result
                    decryptor.read(decryptedBuffer, Unit, this)
                }
            }

            override fun failed(exc: Throwable, attachment: Unit) {
                err = exc
                fut.run()
            }
        }
    }

    init {
        val encryptionChannel = tanker.encrypt(TankerChannels.fromAsynchronousByteChannel(clearChannel)).get()
        decryptor = TankerChannels.toAsynchronousByteChannel(tanker.decrypt(encryptionChannel).get())
    }
}

class StreamChannelTestHelper(tanker: Tanker) {
    val clearChannel = DummyChannel()
    var err: Throwable? = null
    var nbRead = 0
    var decryptor: TankerAsynchronousByteChannel
    val decryptedBuffer = ByteBuffer.allocate(1024 * 1024 * 2)
    val fut = FutureTask {}

    fun callback(): TankerCompletionHandler<Int, Unit> {
        return object : TankerCompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit) {
                if (result == -1) {
                    fut.run()
                } else {
                    nbRead += result
                    decryptor.read(decryptedBuffer, Unit, this)
                }
            }

            override fun failed(exc: Throwable, attachment: Unit) {
                err = exc
                fut.run()
            }
        }
    }

    init {
        decryptor = tanker.decrypt(tanker.encrypt(clearChannel).get()).get()
    }
}


class StreamChannelTests : TankerSpec() {
    lateinit var tanker: Tanker
    lateinit var helper: StreamChannelTestHelper

    override fun beforeTest(description: Description) {
        tanker = Tanker(options.setWritablePath(createTmpDir().toString()))
        val st = tanker.start(tc.createIdentity()).get()
        st shouldBe Status.IDENTITY_REGISTRATION_NEEDED
        tanker.registerIdentity(PassphraseVerification("")).get()
        helper = StreamChannelTestHelper(tanker)
    }

    init {
        "Reading asynchronously" {
            helper.decryptor.read(helper.decryptedBuffer, Unit, helper.callback())
            helper.fut.get()
            helper.err shouldBe null
            helper.nbRead shouldBe helper.decryptedBuffer.capacity()
            helper.clearChannel.clearBuffer.position(0)
            helper.decryptedBuffer shouldBe helper.clearChannel.clearBuffer
        }

        "Reading a closed channel throws" {
            helper.decryptor.read(helper.decryptedBuffer, Unit, helper.callback())
            helper.decryptor.close()
            helper.fut.get()
            helper.err shouldNotBe null
            (helper.err is ClosedChannelException) shouldBe true
        }

        "Attempting two read operations simultaneously throws" {
            val secondBuffer = ByteBuffer.allocate(helper.decryptedBuffer.capacity())
            helper.decryptor.read(helper.decryptedBuffer, Unit, helper.callback())
            shouldThrow<TankerPendingReadException> { helper.decryptor.read(secondBuffer, Unit, helper.callback()) }
        }
    }
}

@RequiresApi(26)
class API26StreamChannelTests : TankerSpec() {
    lateinit var tanker: Tanker
    lateinit var helper: API26StreamChannelTestHelper

    override fun beforeTest(description: Description) {
        tanker = Tanker(options.setWritablePath(createTmpDir().toString()))
        val st = tanker.start(tc.createIdentity()).get()
        st shouldBe Status.IDENTITY_REGISTRATION_NEEDED
        tanker.registerIdentity(PassphraseVerification("")).get()
        helper = API26StreamChannelTestHelper(tanker)
    }

    init {
        "Reading asynchronously" {
            helper.decryptor.read(helper.decryptedBuffer, Unit, helper.callback())
            helper.fut.get()
            helper.err shouldBe null
            helper.nbRead shouldBe helper.decryptedBuffer.capacity()
            helper.dummyChannel.clearBuffer.position(0)
            helper.decryptedBuffer shouldBe helper.dummyChannel.clearBuffer
        }

        "Reading a closed channel throws" {
            helper.decryptor.read(helper.decryptedBuffer, Unit, helper.callback())
            helper.decryptor.close()
            helper.fut.get()
            helper.err shouldNotBe null
            (helper.err is ClosedChannelException) shouldBe true
        }

        "Attempting two read operations simultaneously throws" {
            val secondBuffer = ByteBuffer.allocate(helper.decryptedBuffer.capacity())
            helper.decryptor.read(helper.decryptedBuffer, Unit, helper.callback())
            shouldThrow<ReadPendingException> { helper.decryptor.read(secondBuffer, Unit, helper.callback()) }
        }
    }
}
