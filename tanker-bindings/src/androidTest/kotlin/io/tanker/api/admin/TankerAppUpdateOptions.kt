package io.tanker.api.admin

open class OidcProviderConfig(val clientId: String, val displayName: String, val issuer: String)

open class TankerAppUpdateOptions {
    var oidcProvider: OidcProviderConfig? = null
    var preverifiedVerification: Boolean? = null
    var userEnrollment: Boolean? = null

    fun setOidcProvider(p: OidcProviderConfig?): TankerAppUpdateOptions {
        this.oidcProvider = p
        return this
    }
}
