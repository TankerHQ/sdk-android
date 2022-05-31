package io.tanker.api

import com.sun.jna.Structure

/**
 * Extra options used during identity verification
 */
open class VerificationOptions: Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 2
    @JvmField var withSessionToken: Byte = 0
    @JvmField var allowE2eMethodSwitch: Byte = 0

    /**
    * Requests to create a Session Token on verification
    */
    fun withSessionToken(withSessionToken: Boolean): VerificationOptions {
        this.withSessionToken = if (withSessionToken) 1 else 0
        return this
    }

    /**
     * Allow switching to and from E2E verification methods
     */
    fun allowE2eMethodSwitch(allowE2eMethodSwitch: Boolean): VerificationOptions {
        this.allowE2eMethodSwitch = if (allowE2eMethodSwitch) 1 else 0
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "withSessionToken", "allowE2eMethodSwitch")
    }
}
