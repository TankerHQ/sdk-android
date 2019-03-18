package io.tanker.api

import com.sun.jna.Pointer
import io.tanker.bindings.IdentityLib

class Identity {
    companion object {
        private val libIdentity = IdentityLib.create()

        @JvmStatic fun generate(trustchainId: String, trustchainPrivateKey: String, userId: String): String {
            val tokenCFut = libIdentity.tanker_create_identity(trustchainId, trustchainPrivateKey, userId)
            return TankerFuture<Pointer>(tokenCFut, Pointer::class.java).get().getString(0)
        }

        @JvmStatic fun getPublicIdentity(identity: String): String {
            val tokenCFut = libIdentity.tanker_get_public_identity(identity)
            return TankerFuture<Pointer>(tokenCFut, Pointer::class.java).get().getString(0)
        }
    }
}
