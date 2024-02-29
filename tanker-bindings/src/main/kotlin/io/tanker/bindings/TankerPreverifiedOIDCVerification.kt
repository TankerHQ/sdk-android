package io.tanker.bindings

import com.sun.jna.Structure

class TankerPreverifiedOIDCVerification : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 1
    @JvmField var subject: String? = null
    @JvmField var providerID: String? = null

    fun setSubject(u: String?): TankerPreverifiedOIDCVerification {
        this.subject = u
        return this
    }

    fun setProviderID(u: String?): TankerPreverifiedOIDCVerification {
        this.providerID = u
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "subject", "providerID")
    }
}
