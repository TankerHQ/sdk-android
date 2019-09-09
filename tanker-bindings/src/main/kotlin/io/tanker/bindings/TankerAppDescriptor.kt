package io.tanker.bindings

import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * Describes the main properties of a Tanker app
 */
class TankerAppDescriptor(p: Pointer) : Structure(p) {
    @JvmField var name: String? = null
    @JvmField var id: String? = null
    @JvmField var privateKey: String? = null
    @JvmField var publicKey: String? = null
    init {
        read()
    }

    companion object {
        private val lib = AdminLib.create()
    }

    @Suppress("ProtectedInFinal", "Unused") protected fun finalize() {
        lib.tanker_admin_app_descriptor_free(pointer)
    }

    override fun getFieldOrder(): List<String> {
        return listOf("name", "id", "privateKey", "publicKey")
    }
}
