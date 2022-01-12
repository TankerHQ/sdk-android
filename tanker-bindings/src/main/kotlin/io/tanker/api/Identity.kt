package io.tanker.api

import com.sun.jna.Pointer
import io.tanker.bindings.IdentityLib

class Identity {
    companion object {
        private val libIdentity = IdentityLib.create()

        @JvmStatic fun createIdentity(appId: String, appSecret: String, userId: String): String {
            val identityCFut = libIdentity.tanker_create_identity(appId, appSecret, userId)
            return TankerFuture<Pointer>(identityCFut, Pointer::class.java, keepAlive = this).get().getString(0)
        }

        @JvmStatic fun getPublicIdentity(identity: String): String {
            val identityCFut = libIdentity.tanker_get_public_identity(identity)
            return TankerFuture<Pointer>(identityCFut, Pointer::class.java, keepAlive = this).get().getString(0)
        }

        @JvmStatic fun createProvisionalIdentity(appId: String, email: String): String {
            val identityCFut = libIdentity.tanker_create_provisional_identity(appId, email)
            return TankerFuture<Pointer>(identityCFut, Pointer::class.java, keepAlive = this).get().getString(0)
        }
    }
}
