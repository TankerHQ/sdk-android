package io.tanker.api

import com.sun.jna.Structure

/**
 * Options used for tanker decryption
 *
 * Please use the use the builder interface and the setter methods instead
 * of accessing the fields directly. Those are not part of the public API and
 * are subject to change.
 */
open class TankerDecryptOptions : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var timeout = 10_000L

    /**
     * Sets the maximum time to wait for missing keys while trying to decrypt
     * @param timeoutMilliseconds A number of milliseconds, can be 0 to avoid waiting.
     */
    fun setTimeout(timeoutMilliseconds: Long): TankerDecryptOptions {
        this.timeout = timeoutMilliseconds
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "timeout")
    }
}
