package io.tanker.admin

import com.sun.jna.Structure
import com.sun.jna.ptr.ByteByReference

open class TankerAppUpdateOptions : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField
    var version: Byte = 2
    @JvmField
    var oidcClientId: String? = null
    @JvmField
    var oidcClientProvider: String? = null
    @JvmField
    var sessionCertificates: ByteByReference? = null
    @JvmField
    var preverifiedVerification: ByteByReference? = null

    fun setOidcClientId(u: String?): TankerAppUpdateOptions {
        this.oidcClientId = u
        return this
    }

    fun setOidcClientProvider(u: String?): TankerAppUpdateOptions {
        this.oidcClientProvider = u
        return this
    }

    fun setSessionCertificates(sessionCertificates: Boolean): TankerAppUpdateOptions {
        this.sessionCertificates = if (sessionCertificates) ByteByReference(1) else ByteByReference(0)
        return this
    }

    fun setPreverifiedVerification(preverifiedVerification: Boolean): TankerAppUpdateOptions {
        this.preverifiedVerification = if (preverifiedVerification) ByteByReference(1) else ByteByReference(0)
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "oidcClientId", "oidcClientProvider", "sessionCertificates", "preverifiedVerification")
    }
}
