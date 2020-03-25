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
     * Encrypt data with the session, that can be decrypted with tanker_decrypt
     */
    fun encrypt(data: ByteArray): TankerFuture<ByteArray> {
        val inBuf = Memory(data.size.toLong())
        inBuf.write(0, data, 0, data.size)

        val encryptedSize = lib.tanker_encryption_session_encrypted_size(data.size.toLong())
        val outBuf = Memory(encryptedSize)

        val futurePtr = lib.tanker_encryption_session_encrypt(csess, outBuf, inBuf, data.size.toLong())
        return TankerFuture<Unit>(futurePtr, Unit::class.java).andThen(TankerCallbackWithKeepAlive(keepAlive = inBuf) {
            outBuf.getByteArray(0, encryptedSize.toInt())
        })
    }

    /**
     * Get the session's permanent resource id
     */
    fun getResourceId(): String {
        val fut = lib.tanker_encryption_session_get_resource_id(csess)
        val ptr = TankerFuture<Pointer>(fut, Pointer::class.java).get()
        val str = ptr.getString(0)
        lib.tanker_free_buffer(ptr)
        return str
    }
}