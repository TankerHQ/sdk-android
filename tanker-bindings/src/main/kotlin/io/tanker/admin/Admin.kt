package io.tanker.admin

import com.sun.jna.Pointer
import io.tanker.api.TankerCallback
import io.tanker.api.TankerFuture
import io.tanker.api.TankerVoidCallback
import io.tanker.bindings.TankerAppDescriptor

class Admin(private val adminUrl: String, private val idToken: String, private val apiUrl: String) {
    private var cadmin: Pointer? = null

    companion object {
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
        return TankerFuture<Pointer>(lib.tanker_admin_connect(adminUrl, idToken), Pointer::class.java, lib).andThen(TankerVoidCallback {
            cadmin = it
        })
    }

    fun createApp(name: String): TankerFuture<TankerApp> {
        requireNotNull(cadmin) { "You need to connect() before using the admin API!" }
        val cfut = lib.tanker_admin_create_app(cadmin!!, name)
        return TankerFuture<Pointer>(cfut, Pointer::class.java, lib).andThen(TankerCallback {
            val descriptor = TankerAppDescriptor(it)
            TankerApp(apiUrl, descriptor.id!!, descriptor.authToken!!, descriptor.privateKey!!)
        })
    }

    fun deleteApp(appId: String): TankerFuture<Unit> {
        requireNotNull(cadmin) { "You need to connect() before using the admin API!" }
        return TankerFuture(lib.tanker_admin_delete_app(cadmin!!, appId), Unit::class.java, lib)
    }

    /**
     * Updates the app properties
     */
    fun appUpdate(appId: String, options: TankerAppUpdateOptions): TankerFuture<Unit> {
        requireNotNull(cadmin) { "You need to connect() before using the admin API!" }
        return TankerFuture(lib.tanker_admin_app_update(cadmin!!, appId, options), Unit::class.java, lib)
    }
}
