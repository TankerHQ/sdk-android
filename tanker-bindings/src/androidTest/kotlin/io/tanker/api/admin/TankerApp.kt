package io.tanker.api.admin

import com.sun.jna.Pointer
import io.tanker.api.TankerFuture
import io.tanker.bindings.TankerLib
import io.tanker.api.TankerCallback

class TankerApp(private val url: String, val id: String, val authToken: String, val privateKey: String) {
    companion object {
        private val lib = AdminLib.create()
        private val tankerlib = TankerLib.create()
    }


    fun getEmailVerificationCode(email: String): TankerFuture<String> {
        val fut = TankerFuture<Pointer>(lib.tanker_get_email_verification_code(url, id, authToken, email), Pointer::class.java, lib, keepAlive = this)
        return fut.then(TankerCallback {
            val ptr = it.get()
            val str = ptr.getString(0)
            tankerlib.tanker_free_buffer(ptr)
            str
        })
    }

    fun getSMSVerificationCode(phoneNumber: String): TankerFuture<String> {
        val fut = TankerFuture<Pointer>(lib.tanker_get_sms_verification_code(url, id, authToken, phoneNumber), Pointer::class.java, lib, keepAlive = this)
        return fut.then(TankerCallback {
            val ptr = it.get()
            val str = ptr.getString(0)
            tankerlib.tanker_free_buffer(ptr)
            str
        })
    }
}

