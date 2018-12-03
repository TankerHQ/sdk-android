package io.tanker.api

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import io.tanker.bindings.TankerLib
import kotlin.reflect.jvm.internal.impl.load.java.lazy.JavaTypeQualifiersByElementType

class ChunkEncryptor internal constructor(private val lib: TankerLib, private val CChunkEnc: Pointer)
{
    @Suppress("ProtectedInFinal", "Unused") protected fun finalize() {
        lib.tanker_chunk_encryptor_destroy(CChunkEnc)
    }

    /**
     * @return The size in bytes that needs to be allocated for the seal.
     */
    fun sealSize(): Long {
        return lib.tanker_chunk_encryptor_seal_size(CChunkEnc)
    }


    /**
     * Seal the chunk encryptor.
     * @return A future of the seal.
     */
    fun seal(): TankerFuture<ByteArray> {
        return seal(null)
    }

    /**
     * Seal the chunk encryptor with options.
     * @return A future of the seal.
     */
    fun seal(options: TankerEncryptOptions?): TankerFuture<ByteArray> {
        val sealSize = sealSize()
        val outBuf = Memory(sealSize)

        val futurePtr = lib.tanker_chunk_encryptor_seal(CChunkEnc, outBuf, options)
        return TankerFuture<Unit>(futurePtr, Unit::class.java).then(TankerCallback {
            it.getError()?.let { throw it }
            outBuf.getByteArray(0, sealSize.toInt())
        })
    }

    /**
     * @return The number of chunks (counting holes).
     */
    fun chunkCount(): Long {
        return lib.tanker_chunk_encryptor_chunk_count(CChunkEnc)
    }


    /**
     * Encrypt a chunk of data. The new chunk is appended at the end.
     * @return A future of the encrypted data.
     */
    fun encrypt(data: ByteArray): TankerFuture<ByteArray> {
        return encrypt(data, null)
    }

    /**
     * Encrypt a chunk of data and put it at the given index.
     * @return A future of the encrypted data.
     */
    fun encrypt(data: ByteArray, index: Long?): TankerFuture<ByteArray> {
        val inBuf = Memory(data.size.toLong())
        inBuf.write(0, data, 0, data.size)

        val encryptedSize = lib.tanker_chunk_encryptor_encrypted_size(data.size.toLong())
        val outBuf = Memory(encryptedSize)

        val futurePtr = when (index) {
            null -> lib.tanker_chunk_encryptor_encrypt_append(CChunkEnc, outBuf, inBuf, data.size.toLong())
            else -> lib.tanker_chunk_encryptor_encrypt_at(CChunkEnc, outBuf, inBuf, data.size.toLong(), index)
        }

        return TankerFuture<Unit>(futurePtr, Unit::class.java).then(TankerCallback {
            it.getError()?.let { throw it }
            @Suppress("UNUSED_VARIABLE")
            val keepalive = inBuf
            outBuf.getByteArray(0, encryptedSize.toInt())
        })
    }

    /**
     * Decrypt the data from the chunk encryptor at the given index.
     * @return A future of the decrypted data.
     */
    fun decrypt(data: ByteArray, index: Long): TankerFuture<ByteArray> {
        val inBuf = Memory(data.size.toLong())
        inBuf.write(0, data, 0, data.size)

        val plainSizeFut = TankerFuture<Pointer>(lib.tanker_chunk_encryptor_decrypted_size(inBuf, data.size.toLong()), Pointer::class.java)
        val plainSize = try {
            Pointer.nativeValue(plainSizeFut.get())
        } catch (_: Throwable) {
            return plainSizeFut.transmute(ByteArray::class.java)
        }

        val outBuf = Memory(plainSize)

        val futurePtr = lib.tanker_chunk_encryptor_decrypt(CChunkEnc, outBuf, inBuf, data.size.toLong(), index)
        return TankerFuture<Unit>(futurePtr, Unit::class.java).then(TankerCallback {
            it.getError()?.let { throw it }
            @Suppress("UNUSED_VARIABLE")
            val keepalive = inBuf
            outBuf.getByteArray(0, plainSize.toInt())
        })
    }

    /**
     * Remove a list of chunks at the given indexes from the chunk encryptor.
     * @return A future that resolves when the chunks have been removed.
     */
    fun remove(indexes: LongArray): TankerFuture<Unit> {
        val buf = Memory(indexes.size.toLong() * NativeLong.SIZE)
        for ((index, value) in indexes.withIndex())
            buf.setLong((index * NativeLong.SIZE).toLong(), value)

        val futurePtr = lib.tanker_chunk_encryptor_remove(CChunkEnc, buf, indexes.size.toLong())
        return TankerFuture<Unit>(futurePtr, Unit::class.java).then(TankerCallback {
            it.getError()?.let { throw it }
            @Suppress("UNUSED_VARIABLE")
            val keepalive = buf
        })
    }
}
