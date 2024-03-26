package io.tanker.bindings

import com.sun.jna.Structure

class TankerOIDCAuthorizationCodeVerification : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var providerID: String? = null
    @JvmField var authorizationCode: String? = null
    @JvmField var state: String? = null

    fun setProviderID(s: String?): TankerOIDCAuthorizationCodeVerification {
        this.providerID = s
        return this
    }

    fun setAuthorizationCode(s: String?): TankerOIDCAuthorizationCodeVerification {
        this.authorizationCode = s
        return this
    }

    fun setState(s: String?): TankerOIDCAuthorizationCodeVerification {
        this.state = s
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "providerID", "authorizationCode", "state")
    }
}
