package io.tanker.api

import com.sun.jna.Structure

/**
 * Options that can be given when opening a TankerSession
 *
 * Please use the builder interface and the setter methods instead of accessing the fields directly.
 * Those are not part of the public API and are subject to change.
 */
open class TankerOptions : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var trustchainId: String? = null
    @JvmField var trustchainUrl: String? = null
    @JvmField var writablePath: String? = null

    /**
     * Mandatory. Sets the trustchain to use for the TankerSession.
     */
    fun setTrustchainId(trustchainId: String): TankerOptions {
        this.trustchainId = trustchainId
        return this
    }

    /** @hide */
    fun setTrustchainUrl(trustchainUrl: String): TankerOptions {
        this.trustchainUrl = trustchainUrl
        return this
    }

    /**
     * Mandatory. The path on disk where the Tanker SDK will save data and key material.
     */
    fun setWritablePath(writablePath: String): TankerOptions {
        this.writablePath = writablePath
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "trustchainId", "trustchainUrl", "writablePath")
    }
}
