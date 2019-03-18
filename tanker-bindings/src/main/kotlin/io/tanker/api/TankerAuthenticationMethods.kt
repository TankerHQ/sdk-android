package io.tanker.api

import com.sun.jna.Structure

open class TankerAuthenticationMethods : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var password: String? = null
    @JvmField var email: String? = null

    fun setPassword(u: String?): TankerAuthenticationMethods {
        this.password = u
        return this
    }

    fun setEmail(u: String?): TankerAuthenticationMethods {
        this.email = u
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "password", "email")
    }
}
