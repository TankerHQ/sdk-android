package io.tanker.bindings

import com.sun.jna.Structure

class TankerEmailVerification : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var email: String? = null
    @JvmField var verificationCode: String? = null

    fun setVerificationCode(u: String?): TankerEmailVerification {
        this.verificationCode = u
        return this
    }

    fun setEmail(u: String?): TankerEmailVerification {
        this.email = u
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "email", "verificationCode")
    }
}
