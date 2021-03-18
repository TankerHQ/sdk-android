package io.tanker.api

import com.sun.jna.Structure

/**
 * Extra options used during identity verification
 */
open class VerificationOptions: Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var withSessionToken: Byte = 0

    /**
    * Requests to create a Session Token on verification
    */
    fun withSessionToken(withSessionToken: Boolean): VerificationOptions {
        this.withSessionToken = if (withSessionToken) 1 else 0
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "withSessionToken")
    }
}
