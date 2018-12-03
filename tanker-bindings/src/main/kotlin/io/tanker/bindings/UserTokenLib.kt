package io.tanker.bindings

import com.sun.jna.*
import com.sun.jna.ptr.LongByReference
import io.tanker.api.*

@Suppress("FunctionName")
interface UserTokenLib : Library {
    companion object {
        fun create(): UserTokenLib {
            System.setProperty("jna.debug_load", "true")
            return Native.loadLibrary("tanker", UserTokenLib::class.java)
        }
    }

    fun tanker_generate_user_token(trustchain_id: String, trustchain_private_key: String, user_id: String): ExpectedPointer
}
