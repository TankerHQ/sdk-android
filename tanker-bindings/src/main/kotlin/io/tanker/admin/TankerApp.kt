package io.tanker.admin

import com.sun.jna.Pointer
import io.tanker.api.TankerFuture
import io.tanker.bindings.TankerLib
import io.tanker.api.TankerCallback

class TankerApp(private val url: String, public val id: String, private val authToken: String, public val privateKey: String) {
    companion object {
        private val lib = AdminLib.create()
        private val tankerlib = TankerLib.create()
    }


    fun getVerificationCode(email: String): TankerFuture<String> {
        val fut = TankerFuture<Pointer>(lib.tanker_get_verification_code(url, id, authToken, email), Pointer::class.java, lib)
        return fut.then(TankerCallback {
            val ptr = it.get()
            val str = ptr.getString(0)
            tankerlib.tanker_free_buffer(ptr)
            str
        })
    }

}

