package io.tanker.admin

import com.sun.jna.Pointer
import io.tanker.api.TankerCallback
import io.tanker.api.TankerFuture
import io.tanker.api.TankerVoidCallback
import io.tanker.bindings.TankerLib
import io.tanker.bindings.TankerAppDescriptor

class Admin(private val url: String, private val idToken: String) {
    private var cadmin: Pointer? = null

    companion object {
        private val tankerlib = TankerLib.create()
        private val lib = AdminLib.create()
    }


    @Suppress("ProtectedInFinal", "Unused")
    protected fun finalize() {
        if (cadmin != null) {
            lib.tanker_admin_destroy(cadmin!!)
        }
    }

    /**
     * Authenticate to the Tanker admin server API
     *
     * This must be called before doing any other operation
     */
    fun connect(): TankerFuture<Unit> {
        return TankerFuture<Pointer>(lib.tanker_admin_connect(url, idToken), Pointer::class.java, lib).andThen(TankerVoidCallback {
            cadmin = it
        })
    }

    fun createApp(name: String): TankerFuture<TankerAppDescriptor> {
        if (cadmin == null)
            throw IllegalArgumentException("You need to connect() before using the admin API!")
        val cfut = lib.tanker_admin_create_app(cadmin!!, name)
        return TankerFuture<Pointer>(cfut, Pointer::class.java, lib).andThen(TankerCallback {
            println(it.getPointer(0).getString(0))
            TankerAppDescriptor(it)
        })
    }

    fun deleteApp(appId: String): TankerFuture<Unit> {
        if (cadmin == null)
            throw IllegalArgumentException("You need to connect() before using the admin API!")
        return TankerFuture(lib.tanker_admin_delete_app(cadmin!!, appId), Unit::class.java, lib)
    }

    fun getVerificationCode(appId: String, email: String): TankerFuture<String> {
        if (cadmin == null)
            throw IllegalArgumentException("You need to connect() before using the admin API!")
        val fut = TankerFuture<Pointer>(lib.tanker_admin_get_verification_code(cadmin!!, appId, email), Pointer::class.java, lib)
        return fut.then(TankerCallback {
            val ptr = it.get()
            val str = ptr.getString(0)
            tankerlib.tanker_free_buffer(ptr)
            str
        })
    }
}
