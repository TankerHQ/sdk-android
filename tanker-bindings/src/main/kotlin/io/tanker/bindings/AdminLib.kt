package io.tanker.bindings

import com.sun.jna.*

typealias AdminPointer = Pointer

@Suppress("FunctionName")
interface AdminLib : Library {
    companion object {
        fun create(): AdminLib {
            System.setProperty("jna.debug_load", "true")
            return Native.loadLibrary("tanker_admin-c", AdminLib::class.java)
        }
    }

    fun tanker_admin_connect(url: String, id_token: String): FuturePointer
    fun tanker_admin_create_app(admin: AdminPointer, name: String): FuturePointer
    fun tanker_admin_delete_app(admin: AdminPointer, app_id: String): FuturePointer
    fun tanker_admin_destroy(admin: AdminPointer): FuturePointer
    fun tanker_admin_app_descriptor_free(app: AppDescriptorPointer): Void
    fun tanker_admin_get_verification_code(admin: AdminPointer, app_id: String, email: String): FuturePointer
}
