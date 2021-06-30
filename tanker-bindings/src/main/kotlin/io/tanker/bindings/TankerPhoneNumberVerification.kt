package io.tanker.bindings

import com.sun.jna.Structure

class TankerPhoneNumberVerification : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var phoneNumber: String? = null
    @JvmField var verificationCode: String? = null

    fun setVerificationCode(u: String?): TankerPhoneNumberVerification {
        this.verificationCode = u
        return this
    }

    fun setPhoneNumber(u: String?): TankerPhoneNumberVerification {
        this.phoneNumber = u
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "phoneNumber", "verificationCode")
    }
}
