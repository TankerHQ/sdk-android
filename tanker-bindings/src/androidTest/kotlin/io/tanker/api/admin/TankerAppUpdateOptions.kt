package io.tanker.api.admin

open class OidcProviderConfig(val clientId: String, val displayName: String, val issuer: String, val oidcProviderGroupId: String = "5LOYCcYur5h9k2nMX0GxJ_6xSL4nn4pKNzEAbPFDv3o")

open class TankerAppUpdateOptions {
    var oidcProvider: OidcProviderConfig? = null
    var preverifiedVerification: Boolean? = null

    fun setOidcProvider(p: OidcProviderConfig?): TankerAppUpdateOptions {
        this.oidcProvider = p
        return this
    }
}
