package io.tanker.api

import io.kotlintest.Description
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import java.io.IOException
import java.nio.ByteBuffer

class InputStreamTests : TankerSpec() {
    lateinit var tanker: Tanker
    lateinit var array: ByteArray
    lateinit var buffer: ByteBuffer

    override fun beforeTest(description: Description) {
        tanker = Tanker(options.setWritablePath(createTmpDir().toString()))
        val st = tanker.start(tc.createIdentity()).get()
        st shouldBe Status.IDENTITY_REGISTRATION_NEEDED
        tanker.registerIdentity(PassphraseVerification("")).get()
        array = ByteArray(10)
        buffer = ByteBuffer.allocate(10)
    }

    init {
        "Attempting to decrypt a non encrypted stream throws" {
            val clear = "clear"
            val clearChannel = TankerChannels.fromInputStream(clear.byteInputStream())
            shouldThrow<TankerFutureException> { tanker.decrypt(clearChannel).get() }
        }

        "Attempting to encrypt a closed stream throws" {
            val file = createTempFile()
            val channel = TankerChannels.fromInputStream(file.inputStream())
            val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
            channel.close()
            shouldThrow<IOException> { encryptionStream.read() }
        }

        "Attempting to decrypt a closed  throws" {
            val channel = InputStreamWrapper(array.inputStream())
            val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
            encryptionStream.close()
            shouldThrow<TankerFutureException> { tanker.decrypt(TankerChannels.fromInputStream(encryptionStream)).get() }
        }

        "Reading 0 bytes from a closed stream throws" {
            val channel = TankerChannels.fromInputStream(array.inputStream())
            val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
            encryptionStream.close()
            shouldThrow<IOException> { encryptionStream.read(array, 0, 0) shouldBe 0 }
        }

        "Reading a byte" {
            val channel = TankerChannels.fromInputStream(array.inputStream())
            val decryptionStream = TankerChannels.toInputStream(tanker.decrypt(tanker.encrypt(channel).get()).get())
            decryptionStream.read() shouldBe 0
        }

        "Reading into a whole ByteArray" {
            val channel = TankerChannels.fromInputStream(array.inputStream())
            val decryptionStream = TankerChannels.toInputStream(tanker.decrypt(tanker.encrypt(channel).get()).get())
            val b = ByteArray(10) { 1 }
            decryptionStream.read(b) shouldBe 10
            b shouldBe array
            decryptionStream.read() shouldBe -1
        }

        "Reading 0 bytes should do nothing" {
            val channel = TankerChannels.fromInputStream(array.inputStream())
            val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
            val b = ByteArray(10) { 1 }
            encryptionStream.read(b, 0, 0) shouldBe 0
            b.all { it == 1.toByte() } shouldBe true

            val empty = ByteArray(0)
            encryptionStream.read(empty) shouldBe 0
            empty.size shouldBe 0
        }

        "Giving negative values to read throws" {
            val channel = TankerChannels.fromInputStream(array.inputStream())
            val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
            shouldThrow<IndexOutOfBoundsException> { encryptionStream.read(array, -1, 1) }
            shouldThrow<IndexOutOfBoundsException> { encryptionStream.read(array, 0, -1) }
        }

        "Giving a length larger than buffer size - offset throws" {
            val channel = TankerChannels.fromInputStream(array.inputStream())
            val encryptionStream = TankerChannels.toInputStream(tanker.encrypt(channel).get())
            shouldThrow<IndexOutOfBoundsException> { encryptionStream.read(array, 9, 10) }
        }

        "Reading into a ByteArray twice" {
            val channel = TankerChannels.fromInputStream(array.inputStream())
            val decryptionStream = TankerChannels.toInputStream(tanker.decrypt(tanker.encrypt(channel).get()).get())
            val b = ByteArray(10) { 1 }
            decryptionStream.read(b, 0, 5) shouldBe 5
            decryptionStream.read(b, 5, 5) shouldBe 5
            b shouldBe array
        }
    }
}
