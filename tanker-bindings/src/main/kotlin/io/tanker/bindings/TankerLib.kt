package io.tanker.bindings

import com.sun.jna.*
import io.tanker.api.*

const val HASH_SIZE = 32
const val PRIVATE_SIGNATURE_KEY_SIZE = 64

typealias TankerHttpRequestPointer = Pointer
typealias TankerDatastoreDeviceGetResult = Pointer
typealias TankerDatastoreCacheGetResult = Pointer
typealias SessionPointer = Pointer
typealias ConnectionPointer = Pointer
typealias AppDescriptorPointer = Pointer
typealias StreamInputSourceReadOperationPointer = Pointer
typealias StreamPointer = Pointer
typealias EncryptionSessionPointer = Pointer
// JNA messes up functions that return bool on x86
// so we return Int instead, but we must not forget to take only the first byte of the result
// https://github.com/java-native-access/jna/issues/1076
typealias DangerousNativeBool = Int

interface DatastoreLib {
    interface DatastoreOpenCallback : Callback {
        fun callback(handle: Pointer, db: Pointer, data_path: String, cache_path: String)
    }

    interface DatastoreCloseCallback : Callback {
        fun callback(datastore: Pointer)
    }

    interface DatastoreNukeCallback : Callback {
        fun callback(datastore: Pointer, handle: Pointer)
    }

    interface DatastoreDevicePutCallback : Callback {
        fun callback(datastore: Pointer, handle: Pointer, device: Pointer, size: Int)
    }

    interface DatastoreDeviceGetCallback : Callback {
        fun callback(datastore: Pointer, h: TankerDatastoreDeviceGetResult)
    }

    interface DatastoreCachePutCallback : Callback {
        fun callback(datastore: Pointer, handle: Pointer, keys: Pointer?, key_sizes: Pointer?, values: Pointer?, value_sizes: Pointer?, elem_count: Int, onConflict: Byte)
    }

    interface DatastoreCacheGetCallback : Callback {
        fun callback(datastore: Pointer, h: TankerDatastoreCacheGetResult, keys: Pointer?, key_sizes: Pointer?, elem_count: Int)
    }

    fun tanker_datastore_allocate_device_buffer(result: TankerDatastoreDeviceGetResult, size: Int): Pointer
    fun tanker_datastore_allocate_cache_buffer(result: TankerDatastoreCacheGetResult, data: Pointer, sizes: Pointer): Pointer
    fun tanker_datastore_report_error(handle: Pointer, error_code: Byte, message: String)
}

@Suppress("FunctionName")
interface TankerLib : AsyncLib, DatastoreLib, Library {
    companion object {
        val options = hashMapOf<String, Any>(Library.OPTION_TYPE_MAPPER to TankerTypeMapper())
        fun create(): TankerLib {
            System.setProperty("jna.debug_load", "true")
            return Native.load("ctanker", TankerLib::class.java, options)
        }
    }

    interface HttpSendRequestCallback : Callback {
        fun callback(crequest: TankerHttpRequest, data: Pointer?): Pointer?
    }

    interface HttpCancelRequestCallback : Callback {
        fun callback(crequest: TankerHttpRequest, requestHandle: Pointer?, data: Pointer?)
    }

    interface EventCallback : Callback {
        fun callback(arg: Pointer?)
    }

    interface StreamInputSourceCallback : Callback {
        fun callback(buffer: Pointer, buffer_size: Long, op: StreamInputSourceReadOperationPointer, userArg: Pointer?)
    }

    fun tanker_init(): Void
    fun tanker_shutdown(): Void
    fun tanker_version_string(): String
    fun tanker_create(options: TankerOptions): FuturePointer
    fun tanker_destroy(tanker: SessionPointer): FuturePointer
    fun tanker_start(tanker: SessionPointer, identity: String): FuturePointer
    fun tanker_register_identity(tanker: SessionPointer, tankerVerification: TankerVerification?, verification_options: VerificationOptions?): FuturePointer
    fun tanker_verify_identity(tanker: SessionPointer, tankerVerification: TankerVerification?, verification_options: VerificationOptions?): FuturePointer
    fun tanker_stop(tanker: SessionPointer): FuturePointer
    fun tanker_status(tanker: SessionPointer): Status
    fun tanker_generate_verification_key(tanker: SessionPointer): FuturePointer
    fun tanker_device_id(tanker: SessionPointer): ExpectedPointer
    fun tanker_revoke_device(tanker: SessionPointer, deviceId: String): FuturePointer
    fun tanker_get_device_list(tanker: SessionPointer): FuturePointer

    fun tanker_attach_provisional_identity(tanker: SessionPointer, provisionalIdentity: String): FuturePointer
    fun tanker_verify_provisional_identity(tanker: SessionPointer, verification: TankerVerification): FuturePointer

    fun tanker_get_verification_methods(tanker: SessionPointer): FuturePointer
    fun tanker_set_verification_method(tanker: SessionPointer, verification: TankerVerification, verification_options: VerificationOptions?): FuturePointer

    fun tanker_set_log_handler(handler: LogHandlerCallback): Void
    fun tanker_event_connect(tanker: SessionPointer, event: TankerEvent, callback: EventCallback, user_data: Pointer): ExpectedPointer
    fun tanker_event_disconnect(tanker: SessionPointer, event: TankerEvent): ExpectedPointer

    fun tanker_encrypted_size(clear_size: Long): Long
    fun tanker_decrypted_size(encrypted_data: Pointer, encrypted_size: Long): ExpectedPointer
    fun tanker_get_resource_id(encrypted_data: Pointer, encrypted_size: Long): ExpectedPointer

    fun tanker_encrypt(session: SessionPointer, encrypted_data: Pointer,
                       data: Pointer, data_size: Long, encryption_options: EncryptionOptions?): FuturePointer

    fun tanker_decrypt(session: SessionPointer, decrypted_data: Pointer,
                       data: Pointer, data_size: Long): FuturePointer

    fun tanker_share(session: SessionPointer,
                     resource_ids: StringArray, nbResourceIds: Long,
                     sharingOptions: SharingOptions): FuturePointer

    fun tanker_stream_encrypt(session: SessionPointer, cb: StreamInputSourceCallback, user_data: Pointer?, options: EncryptionOptions?): FuturePointer
    fun tanker_stream_decrypt(session: SessionPointer, cb: StreamInputSourceCallback, user_data: Pointer?): FuturePointer
    fun tanker_stream_read(stream: StreamPointer, buffer: Pointer?, buffer_size: Long): FuturePointer
    fun tanker_stream_read_operation_finish(op: StreamInputSourceReadOperationPointer, nb_read: Long)
    fun tanker_stream_get_resource_id(stream: StreamPointer): ExpectedPointer
    fun tanker_stream_close(stream: StreamPointer): FuturePointer

    fun tanker_encryption_session_open(session: SessionPointer, options: EncryptionOptions): FuturePointer
    fun tanker_encryption_session_close(encSess: EncryptionSessionPointer): FuturePointer
    fun tanker_encryption_session_encrypted_size(clear_size: Long): Long
    fun tanker_encryption_session_get_resource_id(encSess: EncryptionSessionPointer): ExpectedPointer
    fun tanker_encryption_session_encrypt(encSess: EncryptionSessionPointer, encrypted_data: Pointer,
                                          data: Pointer, data_size: Long): FuturePointer

    fun tanker_encryption_session_stream_encrypt(encSess: EncryptionSessionPointer,
                                                 cb: StreamInputSourceCallback,
                                                 user_data: Pointer?): FuturePointer

    fun tanker_create_group(tanker: SessionPointer, member_uids: StringArray, nbMembers: Long): FuturePointer
    fun tanker_update_group_members(tanker: SessionPointer, group_id: String,
                                    users_to_add: StringArray, nb_users_to_add: Long,
                                    users_to_remove: StringArray, nb_users_to_remove: Long): FuturePointer

    fun tanker_http_handle_response(request: TankerHttpRequestPointer, response: TankerHttpResponse)

    fun tanker_free_buffer(buffer: Pointer): Void
    fun tanker_free_device_list(list: Pointer): Void
    fun tanker_free_verification_method_list(list: Pointer): Void

    fun tanker_prehash_password(password: String): ExpectedPointer
}
