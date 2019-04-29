package io.tanker.bindings

import com.sun.jna.*

@Suppress("FunctionName")
interface IdentityLib : Library {
    companion object {
        fun create(): IdentityLib {
            System.setProperty("jna.debug_load", "true")
            return Native.loadLibrary("ctanker", IdentityLib::class.java)
        }
    }

    fun tanker_create_identity(trustchain_id: String, trustchain_private_key: String, user_id: String): ExpectedPointer
    fun tanker_get_public_identity(identity: String): ExpectedPointer
    fun tanker_create_provisional_identity(trustchain_id: String, email: String): ExpectedPointer
}
