package io.tanker.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

class InputStreamTests : TankerSpec() {
    lateinit var tanker: Tanker
    lateinit var array: ByteArray
    lateinit var buffer: ByteBuffer

    @Before
    fun beforeTest() {
        tanker = Tanker(options.setPersistentPath(createTmpDir().toString()).setCachePath(createTmpDir().toString()))
        val st = tanker.start(tc.createIdentity()).get()
        assertThat(st).isEqualTo(Status.IDENTITY_REGISTRATION_NEEDED)
        tanker.registerIdentity(PassphraseVerification("pass")).get()
        array = ByteArray(10)
        buffer = ByteBuffer.allocate(10)
    }

    @After
    fun afterTest() {
        tanker.stop().get()
    }

    @Test
    fun attempting_to_decrypt_a_non_encrypted_stream_throws() {
        val clear = "clear"
        val clearChannel = TankerChannels.fromInputStream(clear.byteInputStream())
        shouldThrow<TankerFutureException> { tanker.decrypt(clearChannel).get() }
    }

    @Test
    fun attempting_to_decrypt_a_closed_stream_throws() {
        val channel = InputStreamWrapper(array.inputStream())
        val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
        encryptionStream.close()
        val e = shouldThrow<TankerFutureException> { tanker.decrypt(TankerChannels.fromInputStream(encryptionStream)).get() }
        assertThat(e).hasCauseInstanceOf(IOException::class.java)
    }

    @Test
    fun reading_0_bytes_from_a_closed_stream_throws() {
        val channel = TankerChannels.fromInputStream(array.inputStream())
        val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
        encryptionStream.close()
        shouldThrow<IOException> { encryptionStream.read(array, 0, 0) }
    }

    @Test
    fun encrypting_decrypting_a_small_buffer() {
        val channel = TankerChannels.fromInputStream(array.inputStream())
        val decryptionStream = TankerChannels.toInputStream(tanker.decrypt(tanker.encrypt(channel).get()).get())
        val b = ByteArray(10) { 1 }
        assertThat(decryptionStream.read(b)).isEqualTo(10)
        assertThat(decryptionStream.read()).isEqualTo(-1)
        assertThat(b).isEqualTo(array)
    }

    @Test
    fun encrypting_decrypting_a_big_buffer() {
        // a chunk is 1MB, make multiple chunks
        val totalLength = 4 * 1024 * 1024
        array = ByteArray(totalLength) { it.toByte() }

        val channel = TankerChannels.fromInputStream(array.inputStream())
        val decryptionStream = TankerChannels.toInputStream(tanker.decrypt(tanker.encrypt(channel).get()).get())
        val b = ByteArray(totalLength)
        var pos = 0
        while (pos < totalLength) {
            val read = decryptionStream.read(b, pos, totalLength - pos)
            pos += read
        }
        // we should be at the end of the buffer
        assertThat(decryptionStream.read()).isEqualTo(-1)

        assertThat(b).isEqualTo(array)
    }

    @Test
    fun canceling_a_read_should_not_crash() {
        // a chunk is 1MB, make multiple chunks
        val totalLength = 4 * 1024 * 1024
        array = ByteArray(totalLength) { it.toByte() }

        val channel = TankerChannels.fromInputStream(array.inputStream())
        val encryptionStream = tanker.encrypt(channel).get()
        val b = ByteBuffer.allocate(totalLength)
        encryptionStream.read(b, Unit, object : TankerCompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit) {
            }

            override fun failed(exc: Throwable, attachment: Unit) {
            }
        })
        encryptionStream.close()
    }

    @Test
    fun reading_0_bytes_should_do_nothing() {
        val channel = TankerChannels.fromInputStream(array.inputStream())
        val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
        val b = ByteArray(10) { 1 }
        assertThat(encryptionStream.read(b, 0, 0)).isEqualTo(0)
        assertThat(b.all { it == 1.toByte() }).isEqualTo(true)

        val empty = ByteArray(0)
        assertThat(encryptionStream.read(empty)).isEqualTo(0)
        assertThat(empty.size).isEqualTo(0)
    }

    @Test
    fun giving_negative_values_to_read_throws() {
        val channel = TankerChannels.fromInputStream(array.inputStream())
        val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
        shouldThrow<IndexOutOfBoundsException> { encryptionStream.read(array, -1, 1) }
        shouldThrow<IndexOutOfBoundsException> { encryptionStream.read(array, 0, -1) }
    }

    @Test
    fun giving_a_length_larger_than_buffer_size_minus_offset_throws() {
        val channel = TankerChannels.fromInputStream(array.inputStream())
        val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
        shouldThrow<IndexOutOfBoundsException> { encryptionStream.read(array, 9, 10) }
    }

    @Test
    fun reading_into_a_ByteArray_twice() {
        val channel = TankerChannels.fromInputStream(array.inputStream())
        val decryptionStream = TankerChannels.toInputStream(tanker.decrypt(tanker.encrypt(channel).get()).get())
        val b = ByteArray(10) { 1 }
        assertThat(decryptionStream.read(b, 0, 5)).isEqualTo(5)
        assertThat(decryptionStream.read(b, 5, 5)).isEqualTo(5)
        assertThat(b).isEqualTo(array)
    }
}
