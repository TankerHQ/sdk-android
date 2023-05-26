package io.tanker.api.admin

open class TankerAppUpdateOptions {
    var oidcClientId: String? = null
    var oidcClientProvider: String? = null
    var preverifiedVerification: Boolean? = null
    var userEnrollment: Boolean? = null

    fun setOidcClientId(u: String?): TankerAppUpdateOptions {
        this.oidcClientId = u
        return this
    }

    fun setOidcClientProvider(u: String?): TankerAppUpdateOptions {
        this.oidcClientProvider = u
        return this
    }
}
