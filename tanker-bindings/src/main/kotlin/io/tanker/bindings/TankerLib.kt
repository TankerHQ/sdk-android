package io.tanker.bindings

import com.sun.jna.*
import com.sun.jna.ptr.LongByReference
import io.tanker.api.*

const val HASH_SIZE = 32
const val PRIVATE_SIGNATURE_KEY_SIZE = 64

typealias FuturePointer = Pointer
typealias ExpectedPointer = FuturePointer
typealias PromisePointer = Pointer
typealias SessionPointer = Pointer
typealias ConnectionPointer = Pointer
typealias AdminPointer = Pointer
typealias TrustchainDescriptorPointer = Pointer
// JNA messes up functions that return bool on x86
// so we return Int instead, but we must not forget to take only the first byte of the result
// https://github.com/java-native-access/jna/issues/1076
typealias DangerousNativeBool = Int

@Suppress("FunctionName")
interface TankerLib : Library {
    companion object {
        val options = hashMapOf<String, Any>(Library.OPTION_TYPE_MAPPER to TankerTypeMapper())
        fun create(): TankerLib {
            System.setProperty("jna.debug_load", "true")
            return Native.loadLibrary("ctanker", TankerLib::class.java, options)
        }
    }

    interface FutureCallback : Callback {
        fun callback(userArg: Pointer?): Pointer
    }

    interface EventCallback : Callback {
        fun callback(arg: Pointer?)
    }

    interface LogHandlerCallback : Callback {
        fun callback(logRecord: TankerLogRecord)
    }

    fun tanker_init(): Void
    fun tanker_version_string(): String
    fun tanker_create(options: TankerOptions): FuturePointer
    fun tanker_destroy(tanker: SessionPointer): FuturePointer
    fun tanker_start(tanker: SessionPointer, identity: String): FuturePointer
    fun tanker_register_identity(tanker: SessionPointer, tankerVerification: TankerVerification?): FuturePointer
    fun tanker_verify_identity(tanker: SessionPointer, tankerVerification: TankerVerification?): FuturePointer
    fun tanker_stop(tanker: SessionPointer): FuturePointer
    fun tanker_status(tanker: SessionPointer): Status
    fun tanker_generate_verification_key(tanker: SessionPointer): FuturePointer
    fun tanker_device_id(tanker: SessionPointer): ExpectedPointer
    fun tanker_revoke_device(tanker: SessionPointer, deviceId: String): FuturePointer
    fun tanker_get_device_list(tanker: SessionPointer): FuturePointer

    fun tanker_attach_provisional_identity(tanker: SessionPointer, provisionalIdentity: String): FuturePointer
    fun tanker_verify_provisional_identity(tanker: SessionPointer, verification: TankerVerification): FuturePointer

    fun tanker_get_verification_methods(tanker: SessionPointer): FuturePointer
    fun tanker_set_verification_method(tanker: SessionPointer, verification: TankerVerification): FuturePointer

    fun tanker_set_log_handler(handler: LogHandlerCallback): Void
    fun tanker_event_connect(tanker: SessionPointer, event: TankerEvent, callback: EventCallback, user_data: Pointer): ExpectedPointer
    fun tanker_event_disconnect(tanker: SessionPointer, event: TankerEvent): ExpectedPointer

    fun tanker_encrypted_size(clear_size: Long): Long
    fun tanker_decrypted_size(encrypted_data: Pointer, encrypted_size: Long): ExpectedPointer
    fun tanker_get_resource_id(encrypted_data: Pointer, encrypted_size: Long): ExpectedPointer

    fun tanker_future_is_ready(future: FuturePointer): DangerousNativeBool
    fun tanker_future_wait(future: FuturePointer): Void
    fun tanker_future_then(future: FuturePointer, callback: FutureCallback, userArg: Pointer): FuturePointer
    fun tanker_future_has_error(future: FuturePointer): DangerousNativeBool
    fun tanker_future_get_error(future: FuturePointer): TankerError
    fun tanker_future_destroy(future: FuturePointer): Void
    fun tanker_future_get_voidptr(future: FuturePointer): Pointer

    fun tanker_promise_create(): PromisePointer
    fun tanker_promise_destroy(promise: PromisePointer): Void
    fun tanker_promise_get_future(promise: PromisePointer): FuturePointer
    fun tanker_promise_set_value(promise: PromisePointer, value: Pointer): Void

    fun tanker_encrypt(session: SessionPointer, encrypted_data: Pointer,
                       data: Pointer, data_size: Long, encrypt_options: TankerEncryptOptions?): FuturePointer
    fun tanker_decrypt(session: SessionPointer, decrypted_data: Pointer,
                       data: Pointer, data_size: Long): FuturePointer
    fun tanker_share(session: SessionPointer, recipient_uids: StringArray, nbrecipientPublicIdentities: Long,
                     recipient_gids: StringArray, nbRecipientGids: Long,
                     resource_ids: StringArray, nbResourceIds: Long): FuturePointer

    fun tanker_create_group(tanker: SessionPointer, member_uids: StringArray, nbMembers: Long): FuturePointer
    fun tanker_update_group_members(tanker: SessionPointer, group_id: String, users_to_add: StringArray, nb_users_to_add: Long): FuturePointer

    fun tanker_base64_encoded_size(decoded_size: Long): Long
    fun tanker_base64_decoded_max_size(encoded_size: Long): Long
    fun tanker_base64_encode(to: Pointer, from: Pointer, from_size: Long): Void
    fun tanker_base64_decode(to: Pointer, to_size: LongByReference, from: Pointer, from_size: Long): Void

    fun tanker_admin_connect(trustchain_url: String, id_token: String): FuturePointer
    fun tanker_admin_create_trustchain(admin: AdminPointer, name: String): FuturePointer
    fun tanker_admin_delete_trustchain(admin: AdminPointer, trustchain_id: String): FuturePointer
    fun tanker_admin_destroy(admin: AdminPointer): FuturePointer
    fun tanker_admin_trustchain_descritor_free(trustchain: TrustchainDescriptorPointer): Void
    fun tanker_admin_get_verification_code(admin: AdminPointer, trustchain_id: String, email: String): FuturePointer

    fun tanker_free_buffer(buffer: Pointer): Void
    fun tanker_free_device_list(list: Pointer): Void
    fun tanker_free_verification_method_list(list: Pointer): Void
}
