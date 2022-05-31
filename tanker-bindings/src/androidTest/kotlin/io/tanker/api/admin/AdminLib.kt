package io.tanker.api.admin

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

    fun tanker_admin_connect(app_management_url: String, app_management_token: String, environment_name: String): FuturePointer
    fun tanker_admin_create_app(admin: AdminPointer, name: String): FuturePointer
    fun tanker_admin_delete_app(admin: AdminPointer, app_id: String): FuturePointer
    fun tanker_admin_destroy(admin: AdminPointer): FuturePointer
    fun tanker_admin_app_descriptor_free(app: AppDescriptorPointer): Void
    fun tanker_admin_app_update(admin: AdminPointer, app_id: String, options: TankerAppUpdateOptions): FuturePointer
    fun tanker_get_email_verification_code(url: String, app_id: String, auth_token: String, email: String): FuturePointer
    fun tanker_get_sms_verification_code(url: String, app_id: String, auth_token: String, phone_number: String): FuturePointer
}
