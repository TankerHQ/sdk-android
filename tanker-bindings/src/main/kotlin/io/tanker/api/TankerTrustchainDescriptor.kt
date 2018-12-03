package io.tanker.api

import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.tanker.bindings.TankerLib

/**
 * Describes the main properties of a trustchain
 */
class TankerTrustchainDescriptor(p: Pointer) : Structure(p) {
    @JvmField var name: String? = null
    @JvmField var id: String? = null
    @JvmField var privateKey: String? = null
    @JvmField var publicKey: String? = null
    init {
        read()
    }

    companion object {
        private val lib = TankerLib.create()
    }

    @Suppress("ProtectedInFinal", "Unused") protected fun finalize() {
        lib.tanker_admin_trustchain_descritor_free(pointer)
    }

    override fun getFieldOrder(): List<String> {
        return listOf("name", "id", "privateKey", "publicKey")
    }
}
