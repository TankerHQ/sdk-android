package io.tanker.api

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.filters.SdkSuppress
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.CompletionHandler
import java.nio.channels.ReadPendingException
import java.util.concurrent.FutureTask

class BlockingChannel : TankerAsynchronousByteChannel {
    private var isClosed = false

    override fun <A> read(
        dst: ByteBuffer,
        attachment: A,
        handler: TankerCompletionHandler<Int, in A>
    ) {
    }

    override fun isOpen(): Boolean {
        return !isClosed
    }

    override fun close() {
        isClosed = true
    }
}

class DummyChannel : TankerAsynchronousByteChannel {
    val clearBuffer = ByteBuffer.allocate(1024 * 1024 * 2)
    private var isClosed = false

    override fun <A> read(
        dst: ByteBuffer,
        attachment: A,
        handler: TankerCompletionHandler<Int, in A>
    ) {
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
class API26StreamChannelTestHelper(
    tanker: Tanker,
    chan: TankerAsynchronousByteChannel,
    decrypt: Boolean
) {
    val dummyChannel = DummyChannel()
    val clearChannel = TankerChannels.toAsynchronousByteChannel(dummyChannel)
    var err: Throwable? = null
    var nbRead = 0
    var outputChannel: AsynchronousByteChannel
    val decryptedBuffer = ByteBuffer.allocate(1024 * 1024 * 2)
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
@SdkSuppress(minSdkVersion = 26)
class API26StreamChannelTests : TankerSpec() {
    lateinit var tanker: Tanker

    @Before
    fun beforeTest() {
        tanker = Tanker(options.setPersistentPath(createTmpDir()).setCachePath(createTmpDir()))
        val st = tanker.start(tc.createIdentity()).get()
        assertThat(st).isEqualTo(Status.IDENTITY_REGISTRATION_NEEDED)
        tanker.registerIdentity(PassphraseVerification("pass")).get()
    }

    @After
    fun afterTest() {
        tanker.stop().get()
    }

    @Test
    fun reading_asynchronously() {
        val helper = API26StreamChannelTestHelper(tanker, DummyChannel(), true)
        helper.outputChannel.read(helper.decryptedBuffer, Unit, helper.callback())
        helper.fut.get()
        assertThat(helper.err).isEqualTo(null)
        assertThat(helper.nbRead).isEqualTo(helper.decryptedBuffer.capacity())
        helper.dummyChannel.clearBuffer.position(0)
        assertThat(helper.decryptedBuffer).isEqualTo(helper.dummyChannel.clearBuffer)
    }

    @Test
    fun attempting_two_read_operations_simultaneously_throws() {
        val helper = API26StreamChannelTestHelper(tanker, BlockingChannel(), false)
        val secondBuffer = ByteBuffer.allocate(helper.decryptedBuffer.capacity())
        helper.outputChannel.read(helper.decryptedBuffer, Unit, helper.callback())
        shouldThrow<ReadPendingException> {
            helper.outputChannel.read(
                secondBuffer,
                Unit,
                helper.callback()
            )
        }
    }
}
