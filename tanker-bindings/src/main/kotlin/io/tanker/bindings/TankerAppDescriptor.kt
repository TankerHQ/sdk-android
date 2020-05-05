package io.tanker.bindings

import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.tanker.admin.AdminLib
import io.tanker.api.TankerFuture
import io.tanker.api.TankerCallback

/**
 * Describes the main properties of a Tanker app
 */
class TankerAppDescriptor(p: Pointer) : Structure(p) {
    @JvmField var name: String? = null
    @JvmField var id: String? = null
    @JvmField var authToken: String? = null
    @JvmField var privateKey: String? = null
    @JvmField var publicKey: String? = null
    init {
        read()
    }

    companion object {
        private val tankerlib = TankerLib.create()
        private val lib = AdminLib.create()
    }

    @Suppress("ProtectedInFinal", "Unused") protected fun finalize() {
        lib.tanker_admin_app_descriptor_free(pointer)
    }

    override fun getFieldOrder(): List<String> {
        return listOf("name", "id", "authToken", "privateKey", "publicKey")
    }


    fun getVerificationCode(url: String, email: String): TankerFuture<String> {
        val fut = TankerFuture<Pointer>(lib.tanker_get_verification_code(url, id!!, authToken!!, email), Pointer::class.java, lib)
        return fut.then(TankerCallback {
            val ptr = it.get()
            val str = ptr.getString(0)
            tankerlib.tanker_free_buffer(ptr)
            str
        })
    }
}
