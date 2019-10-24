package io.tanker.admin

import com.sun.jna.*
import io.tanker.bindings.AppDescriptorPointer
import io.tanker.bindings.AsyncLib
import io.tanker.bindings.FuturePointer

typealias AdminPointer = Pointer

@Suppress("FunctionName")
interface AdminLib : AsyncLib, Library {
    companion object {
        fun create(): AdminLib {
            System.setProperty("jna.debug_load", "true")
            return Native.load("tanker_admin-c", AdminLib::class.java)
        }
    }

    fun tanker_admin_connect(url: String, id_token: String): FuturePointer
    fun tanker_admin_create_app(admin: AdminPointer, name: String): FuturePointer
    fun tanker_admin_delete_app(admin: AdminPointer, app_id: String): FuturePointer
    fun tanker_admin_destroy(admin: AdminPointer): FuturePointer
    fun tanker_admin_app_descriptor_free(app: AppDescriptorPointer): Void
    fun tanker_admin_get_verification_code(admin: AdminPointer, app_id: String, email: String): FuturePointer
    fun tanker_admin_app_update(admin: AdminPointer, app_id: String, oidc_client_id: String, oidc_client_provider: String): FuturePointer
}
