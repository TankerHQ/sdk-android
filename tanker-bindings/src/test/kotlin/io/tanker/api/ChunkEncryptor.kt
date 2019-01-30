package io.tanker.api

import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.tanker.bindings.TankerErrorCode
import java.util.*

class ChunkEncryptorTests : TankerSpec() {
    lateinit var tanker: Tanker

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        tanker = Tanker(options)
        val userId = UUID.randomUUID().toString()
        val token = tc.generateUserToken(userId)
        tanker.open(userId, token).get()
    }

    init {
        "Constructing a ChunkEncryptor should not fail" {
            run {
                tanker.makeChunkEncryptor().get()
            }
            System.gc()
        }

        "ChunkCount is 0 in a new ChunkEncryptor" {
            val chunk = tanker.makeChunkEncryptor().get()
            chunk.chunkCount() shouldBe 0L
        }

        "ChunkCount is 1 if one chunk is appended" {
            val chunk = tanker.makeChunkEncryptor().get()
            chunk.encrypt(byteArrayOf(0)).get()
            chunk.chunkCount() shouldBe 1L
        }

        "SealSize should be nonzero if we appended a chunk" {
            val chunk = tanker.makeChunkEncryptor().get()
            val oldSealSize = chunk.sealSize()
            chunk.encrypt(byteArrayOf(0)).get()
            val newSealSize = chunk.sealSize()
            assert(newSealSize > oldSealSize)
        }

        "An encrypted chunk isn't smaller than the raw data" {
            val data = byteArrayOf(1, 2, 3, 4, 5)

            val chunk = tanker.makeChunkEncryptor().get()
            val encrypted = chunk.encrypt(data).get()

            assert(encrypted.size > data.size)
        }

        "SealSize should match if we do a roundtrip" {
            val chunk = tanker.makeChunkEncryptor().get()
            chunk.encrypt(byteArrayOf(0)).get()
            val seal = chunk.seal().get()

            val chunk2 = tanker.makeChunkEncryptor(seal).get()
            chunk.sealSize() shouldBe chunk2.sealSize()
        }

        "SealSize and ChunkCount don't change after an encrypt of an existing chunk" {
            val data1 = byteArrayOf(1, 2, 3, 4, 5)
            val data2 = byteArrayOf(8, 9, 10)

            val chunk = tanker.makeChunkEncryptor().get()
            chunk.encrypt(data1).block()
            val prevSealSize = chunk.sealSize()
            val prevChunkCount = chunk.chunkCount()

            chunk.encrypt(data2, 0).block()

            chunk.sealSize() shouldBe prevSealSize
            chunk.chunkCount() shouldBe prevChunkCount
        }

        "Encrypt(chunkCount()) should add a new chunk" {
            val data1 = byteArrayOf(1, 2, 3, 4, 5)
            val data2 = byteArrayOf(8, 9, 10)

            val chunk = tanker.makeChunkEncryptor().get()
            chunk.encrypt(data1).block()
            val prevSealSize = chunk.sealSize()
            val prevChunkCount = chunk.chunkCount()

            chunk.encrypt(data2, chunk.chunkCount()).block()

            assert(chunk.sealSize() > prevSealSize)
            chunk.chunkCount() shouldBe prevChunkCount + 1
        }

        "Encrypt(chunkCount()+3) should add 3 holes and 1 new data chunk" {
            val data1 = byteArrayOf(1, 2, 3, 4, 5)
            val data2 = byteArrayOf(8, 9, 10)

            val chunk = tanker.makeChunkEncryptor().get()
            chunk.encrypt(data1).block()
            val prevSealSize = chunk.sealSize()
            val prevChunkCount = chunk.chunkCount()

            chunk.encrypt(data2, chunk.chunkCount()+3).block()

            assert(chunk.sealSize() > prevSealSize)
            chunk.chunkCount() shouldBe prevChunkCount + 4

            chunk.encrypt(data2, prevChunkCount+0).block()
            chunk.encrypt(data2, prevChunkCount+1).block()
            chunk.encrypt(data2, prevChunkCount+2).block()
            chunk.chunkCount() shouldBe prevChunkCount + 4
        }

        "Can encrypt and decrypt back" {
            val data = byteArrayOf(1, 2, 3, 4, 5)

            val chunk = tanker.makeChunkEncryptor().get()
            val encrypted = chunk.encrypt(data).get()
            val decrypted = chunk.decrypt(encrypted, 0).get()

            assert(data.contentEquals(decrypted))
        }

        "Decrypt at a bad index throws properly" {
            val data = byteArrayOf(1, 2, 3, 4, 5)

            val chunk = tanker.makeChunkEncryptor().get()
            val encrypted = chunk.encrypt(data).get()
            val e = shouldThrow<TankerFutureException> {
                chunk.decrypt(encrypted, 5).get()
            }
            assert(e.cause is TankerException)
            assert((e.cause as TankerException).errorCode == TankerErrorCode.CHUNK_INDEX_OUT_OF_RANGE)
        }

        "Can remove a single chunk" {
            val data = byteArrayOf(1, 2, 3, 4, 5)

            val chunk = tanker.makeChunkEncryptor().get()
            chunk.encrypt(data).get()
            chunk.chunkCount() shouldBe 1L

            chunk.remove(longArrayOf(0)).get()
            chunk.chunkCount() shouldBe 0L
        }

        "Can remove holes, and chunks" {
            val data = byteArrayOf(1, 2, 3, 4, 5)
            val chunk = tanker.makeChunkEncryptor().get()

            chunk.encrypt(data).get()
            chunk.encrypt(data, 2).get()
            val encrypted = chunk.encrypt(data).get()
            chunk.remove(longArrayOf(0, 2)).get()

            assert(chunk.chunkCount() == 2L)
            assert(chunk.decrypt(encrypted, 1).get().contentEquals(data))
        }

        "Remove fails if the index is out of range" {
            val data = byteArrayOf(1, 2, 3, 4, 5)
            val chunk = tanker.makeChunkEncryptor().get()

            chunk.encrypt(data).get()
            shouldThrow<TankerFutureException> {
                chunk.remove(longArrayOf(8)).get()
            }
        }
    }
}
