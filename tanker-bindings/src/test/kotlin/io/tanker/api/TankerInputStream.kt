package io.tanker.api

import io.kotlintest.Description
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import java.io.IOException
import java.io.InputStream

class InputStreamTests : TankerSpec() {
    lateinit var tanker: Tanker
    lateinit var buffer: ByteArray

    override fun beforeTest(description: Description) {
        tanker = Tanker(options.setWritablePath(createTmpDir().toString()))
        val st = tanker.start(tc.createIdentity()).get()
        st shouldBe Status.IDENTITY_REGISTRATION_NEEDED
        tanker.registerIdentity(PassphraseVerification("")).get()
        buffer = ByteArray(10)
    }

    init {
        "Attempting to decrypt a non encrypted stream throws" {
            val clear = "clear"
            shouldThrow<TankerFutureException> { tanker.decrypt(clear.byteInputStream()).get() }
        }

        "Attempting to encrypt a closed stream throws" {
            val file = createTempFile()
            val stream = file.inputStream()
            val encryptor = tanker.encrypt(stream).get()
            stream.close()
            shouldThrow<IOException> { encryptor.read() }
        }

        "Attempting to decrypt a closed stream throws" {
            val encryptor = tanker.encrypt(buffer.inputStream()).get()
            encryptor.close()
            shouldThrow<TankerFutureException> { tanker.decrypt(encryptor).get() }
        }

        "Reading 0 bytes from a closed stream does nothing" {
            val encryptor = tanker.encrypt(buffer.inputStream()).get()
            encryptor.close()
            encryptor.read(buffer, 0 , 0) shouldBe 0
        }

        "Reading a byte" {
            val decryptor = tanker.decrypt(tanker.encrypt(buffer.inputStream()).get()).get()
            decryptor.read() shouldBe 0
        }

        "Reading into a whole ByteArray" {
            val decryptor = tanker.decrypt(tanker.encrypt(buffer.inputStream()).get()).get()
            val b = ByteArray(10) { 1 }
            decryptor.read(b) shouldBe 10
            b shouldBe buffer
            decryptor.read() shouldBe -1
        }

        "Reading 0 bytes should do nothing" {
            val encryptor = tanker.encrypt(buffer.inputStream()).get()
            val b = ByteArray(10) { 1 }
            encryptor.read(b, 0, 0) shouldBe 0
            b.all { it == 1.toByte() } shouldBe true

            val empty = ByteArray(0)
            encryptor.read(empty) shouldBe 0
            empty.size shouldBe 0
        }

        "Giving negative values to read throws" {
            val encryptor = tanker.encrypt(buffer.inputStream()).get()
            shouldThrow<IndexOutOfBoundsException> { encryptor.read(buffer, -1, 1) }
            shouldThrow<IndexOutOfBoundsException> { encryptor.read(buffer, 0, -1)}
        }

        "Giving a length larger than buffer size - offset throws" {
            val encryptor = tanker.encrypt(buffer.inputStream()).get()
            shouldThrow<IndexOutOfBoundsException> { encryptor.read(buffer, 9, 10) }
        }

        "Reading into a ByteArray twice" {
            val decryptor = tanker.decrypt(tanker.encrypt(buffer.inputStream()).get()).get()
            val b = ByteArray(10) { 1 }
            decryptor.read(b, off = 0, len = 5) shouldBe 5
            decryptor.read(b, off = 5, len = 5) shouldBe 5
            b shouldBe buffer
        }
    }
}
