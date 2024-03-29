package io.tanker.api

import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.tanker.bindings.TankerLib

class EncryptionSession(private val csess: Pointer) {
    companion object {
        internal val lib = TankerLib.create()
    }

    @Suppress("ProtectedInFinal", "Unused")
    protected fun finalize() {
        lib.tanker_encryption_session_close(csess)
    }

    /**
     * Encrypt data with the session, that can be decrypted with Tanker.decrypt
     */
    fun encrypt(data: ByteArray): TankerFuture<ByteArray> {
        val inBuf = Memory(data.size.toLong().coerceAtLeast(1))
        inBuf.write(0, data, 0, data.size)

        val encryptedSize = lib.tanker_encryption_session_encrypted_size(csess, data.size.toLong())
        val outBuf = Memory(encryptedSize)

        val futurePtr = lib.tanker_encryption_session_encrypt(csess, outBuf, inBuf, data.size.toLong())
        return TankerFuture<Unit>(futurePtr, Unit::class.java, keepAlive = this).andThen(TankerCallback {
            outBuf.getByteArray(0, encryptedSize.toInt())
        })
    }

    /**
     * Encrypt a data stream with the session, that can be decrypted with Tanker.decrypt
     */
    fun encrypt(channel: TankerAsynchronousByteChannel): TankerFuture<TankerAsynchronousByteChannel> {
        val cb = TankerStreamInputSourceCallback(channel)
        val futurePtr = Tanker.lib.tanker_encryption_session_stream_encrypt(csess, cb, null)
        return TankerFuture<Pointer>(futurePtr, Pointer::class.java, keepAlive = this).andThen(TankerCallback {
            TankerStream(it, cb)
        })
    }

    /**
     * Get the session's resource id
     */
    fun getResourceId(): String {
        val fut = lib.tanker_encryption_session_get_resource_id(csess)
        val ptr = TankerFuture<Pointer>(fut, Pointer::class.java, keepAlive = this).get()
        val str = ptr.getString(0)
        lib.tanker_free_buffer(ptr)
        return str
    }
}
