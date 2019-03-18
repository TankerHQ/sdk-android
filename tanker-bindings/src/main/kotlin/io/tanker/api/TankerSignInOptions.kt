package io.tanker.api

import com.sun.jna.Structure

/**
 * Options used for Tanker sign in
 */
open class TankerSignInOptions : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var unlockKey: String? = null
    @JvmField var verificationCode: String? = null
    @JvmField var password: String? = null

    fun setUnlockKey(u: String?): TankerSignInOptions {
        this.unlockKey = u
        return this
    }

    fun setVerificationCode(u: String?): TankerSignInOptions {
        this.verificationCode = u
        return this
    }

    fun setPassword(u: String?): TankerSignInOptions {
        this.password = u
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "unlockKey", "verificationCode", "password")
    }
}
