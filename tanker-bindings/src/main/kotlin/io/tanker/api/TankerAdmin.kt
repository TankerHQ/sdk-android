package io.tanker.api

import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.tanker.bindings.TankerLib

/**
 * The Tanker admin API allows managing your Trustchains.
 *
 * @param trustchainUrl The URL of the tanker server to connect to
 * @param idToken The authentication token string for the admin API
 */
class TankerAdmin(private val trustchainUrl: String, private val idToken: String) {
    private var cadmin: Pointer? = null

    companion object {
        private val lib = TankerLib.create()
    }


    @Suppress("ProtectedInFinal", "Unused") protected fun finalize() {
        if (cadmin != null)
            lib.tanker_admin_destroy(cadmin!!)
    }

    /**
     * Authenticate to the Tanker admin server API
     *
     * This must be called before doing any other operation
     */
    fun connect(): TankerFuture<Unit> {
        return TankerFuture<Pointer>(lib.tanker_admin_connect(trustchainUrl, idToken), Pointer::class.java).andThen(TankerVoidCallback {
            cadmin = it
        })
    }

    fun createTrustchain(name: String): TankerFuture<TankerTrustchainDescriptor> {
        if (cadmin == null)
            throw IllegalArgumentException("You need to connect() before using the admin API!")
        val cfut = lib.tanker_admin_create_trustchain(cadmin!!, name)
        return TankerFuture<Pointer>(cfut, Pointer::class.java).andThen(TankerCallback {
            println(it.getPointer(0).getString(0))
            TankerTrustchainDescriptor(it)
        })
    }

    fun deleteTrustchain(trustchainId: String): TankerFuture<Unit> {
        if (cadmin == null)
            throw IllegalArgumentException("You need to connect() before using the admin API!")
        return TankerFuture(lib.tanker_admin_delete_trustchain(cadmin!!, trustchainId), Unit::class.java)
    }
}
