package io.tanker.api

import com.sun.jna.Pointer
import io.tanker.bindings.TankerLib
import io.tanker.bindings.UserTokenLib

class UserToken {
    companion object {
        private val libToken = UserTokenLib.create()

        @JvmStatic fun generate(trustchainId: String, trustchainPrivateKey: String, userId: String): String {
            val tokenCFut = libToken.tanker_generate_user_token(trustchainId, trustchainPrivateKey, userId)
            return TankerFuture<Pointer>(tokenCFut, Pointer::class.java).get().getString(0)
        }
    }
}
