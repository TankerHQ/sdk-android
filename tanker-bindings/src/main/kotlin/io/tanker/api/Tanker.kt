package io.tanker.api

import android.util.Log
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import io.tanker.bindings.*
import io.tanker.jni.KVMx86Bug

/**
 * Main entry point for the Tanker SDK. Can open a TankerSession.
 */
class Tanker(tankerOptions: TankerOptions) {
    companion object {
        private const val LOG_TAG = "io.tanker.sdk"
        private const val TANKER_ANDROID_VERSION = "dev"

        internal val lib = TankerLib.create()

        @ProguardKeep
        private var logCallbackLifeSupport: LogHandlerCallback? = null

        /**
         * @return The version string of the native Tanker SDK.
         */
        @JvmStatic
        fun getVersionString(): String {
            return TANKER_ANDROID_VERSION
        }

        @JvmStatic
        fun getNativeVersionString(): String {
            return lib.tanker_version_string()
        }

        /**
         * Sets the global tanker log handler.
         * Log messages generated by the Tanker SDK will go through this handler.
         */
        @JvmStatic
        fun setLogHandler(handler: LogHandlerCallback) {
            logCallbackLifeSupport = handler
            lib.tanker_set_log_handler(handler)
        }

        @JvmStatic
        fun prehashPassword(password: String): String {
            val fut = TankerFuture<Pointer>(lib.tanker_prehash_password(password), Pointer::class.java)
            val ptr = fut.get()
            val str = ptr.getString(0)
            lib.tanker_free_buffer(ptr)
            return str
        }
    }

    private val tanker: Pointer
    private var deviceRevokedHandlers = mutableListOf<TankerDeviceRevokedHandler>()

    @ProguardKeep
    private var callbacksLifeSupport = mutableListOf<Any>()

    init {
        lib.tanker_init()

        if (KVMx86Bug.hasBug()) {
            Log.w("io.tanker.sdk", "Warning: The tanker SDK detected that it is running on an x86 emulator with KVM enabled.\n"
                    + "A hardware-acceleration bug in some versions of the x86_32 Android emulator causes it to crash when starting Tanker.\n"
                    + "If you encounter a crash only in the x86 emulator, please make sure to update Android Studio (or use an x86_64 emulator)")
        }

        val createFuture = lib.tanker_create(tankerOptions)
        tanker = TankerFuture<Pointer>(createFuture, Pointer::class.java).get()

        connectInternalHandler(TankerEvent.DEVICE_REVOKED, this::triggerDeviceRevokedEvent)
    }

    private fun connectInternalHandler(e: TankerEvent, f: () -> Unit) {
        val callbackWrapper = object : TankerLib.EventCallback {
            override fun callback(arg: Pointer?) {
                // We don't want to let the user run code directly on the callback thread,
                // because blocking in the callback prevents progress and could deadlock us.
                // Instead we wrap in a future to move the handler to another thread, and run asynchronously
                TankerFuture.threadPool.execute {
                    f()
                }
            }
        }
        val fut = lib.tanker_event_connect(tanker, e, callbackWrapper, Pointer(0))
        TankerFuture<Unit>(fut, Unit::class.java).get()
        callbacksLifeSupport.add(callbackWrapper)
    }

    @Suppress("ProtectedInFinal", "Unused")
    protected fun finalize() {
        lib.tanker_destroy(tanker)
    }

    /**
     * Starts a Tanker session.
     * If the method returns READY, the session is started. If it returns
     * IDENTITY_REGISTRATION_NEEDED, you must call registerIdentity. If it returns
     * IDENTITY_VERIFICATION_NEEDED, you must call verifyIdentity.
     * @param identity The identity of the user to start the session for.
     * @return A future that resolves with a Status
     */
    fun start(identity: String): TankerFuture<Status> {
        val futurePtr = lib.tanker_start(tanker, identity)
        return TankerFuture<Int>(futurePtr, Int::class.java).andThen(TankerCallback {
            Status.fromInt(it)
        })
    }

    /**
     * Registers a new identity and finishes opening a Tanker session.
     * @param verification A verification option to set up how the user's identity will be verified later
     * @return A future that resolves when the session is open
     */
    fun registerIdentity(verification: Verification): TankerFuture<Unit> {
        val futurePtr = lib.tanker_register_identity(tanker, verification.toCVerification())
        return TankerFuture(futurePtr, Unit::class.java)
    }

    /**
     * Verifies an identity and finishes opening a Tanker session.
     * @param verification A verification option that verifies the user's identity
     * @return A future that resolves when the session is open
     */
    fun verifyIdentity(verification: Verification): TankerFuture<Unit> {
        val futurePtr = lib.tanker_verify_identity(tanker, verification.toCVerification())
        return TankerFuture(futurePtr, Int::class.java)
    }

    /**
     * Stops a Tanker session.
     * @return A future that resolves when the session is stopped.
     */
    fun stop(): TankerFuture<Unit> {
        val futurePtr = lib.tanker_stop(tanker)
        return TankerFuture(futurePtr, Unit::class.java)
    }

    /**
     * Attaches a provisional identity to the current user.
     * @return A future that resolves when the claim is successful
     */
    fun attachProvisionalIdentity(provisionalIdentity: String): TankerFuture<AttachResult> {
        val fut = TankerFuture<Pointer>(lib.tanker_attach_provisional_identity(tanker, provisionalIdentity), Pointer::class.java)
        return fut.then(TankerCallback {
            val attachResultPtr = it.get()
            val status = attachResultPtr.getByte(1).toInt()
            val method = attachResultPtr.getPointer(Native.POINTER_SIZE.toLong())
            var outMethod: VerificationMethod? = null
            if (method != Pointer.NULL) {
                outMethod = verificationMethodFromCVerification(TankerVerificationMethod(method))
            }
            AttachResult(Status.fromInt(status), outMethod)
        })
    }

    fun verifyProvisionalIdentity(verification: Verification): TankerFuture<Unit> {
        return TankerFuture(lib.tanker_verify_provisional_identity(tanker, verification.toCVerification()), Unit::class.java)
    }

    /**
     * Get whether the Tanker session is open.
     */
    fun getStatus(): Status {
        return lib.tanker_status(tanker)
    }

    /**
     * Gets the current device's ID as a string
     */
    fun getDeviceId(): String {
        val fut = TankerFuture<Pointer>(lib.tanker_device_id(tanker), Pointer::class.java)
        return fut.then<String>(TankerCallback {
            val ptr = it.get()
            val str = ptr.getString(0)
            lib.tanker_free_buffer(ptr)
            str
        }).get()
    }

    /**
     * Gets the list of the user's devices
     */
    fun getDeviceList(): TankerFuture<List<DeviceInfo>> {
        val fut = TankerFuture<Pointer>(lib.tanker_get_device_list(tanker), Pointer::class.java)
        return fut.then(TankerCallback {
            val devListPtr = it.get()
            val count = devListPtr.getInt(Native.POINTER_SIZE.toLong())
            val finalizer = TankerDeviceListFinalizer(lib, devListPtr)
            val firstDevice = DeviceInfo(devListPtr.getPointer(0))
            if (count == 0) {
                listOf()
            } else {
                @Suppress("UNCHECKED_CAST")
                val devices = firstDevice.toArray(count) as Array<DeviceInfo>
                for (device in devices)
                    device.finalizer = finalizer
                devices.toList()
            }
        })
    }

    /**
     * Revoke a device by device id.
     */
    @Deprecated("The deviceRevoked method is deprecated, it will be removed in the future")
    fun revokeDevice(deviceId: String): TankerFuture<Unit> {
        return TankerFuture(lib.tanker_revoke_device(tanker, deviceId), Unit::class.java)
    }

    /**
     * Generates and registers an verification key that can be used to verify a device.
     * @return The verification key.
     */
    fun generateVerificationKey(): TankerFuture<String> {
        val fut = TankerFuture<Pointer>(lib.tanker_generate_verification_key(tanker), Pointer::class.java)
        return fut.then(TankerCallback {
            val ptr = it.get()
            val str = ptr.getString(0)
            lib.tanker_free_buffer(ptr)
            str
        })
    }

    /**
     * Returns the list of currently registered verification methods.
     * Must be called on an already opened Session.
     * @return Ordered list of verification methods that are currently set-up.
     */
    fun getVerificationMethods(): TankerFuture<List<VerificationMethod>> {
        val fut = TankerFuture<Pointer>(lib.tanker_get_verification_methods(tanker), Pointer::class.java)
        return fut.then(TankerCallback {
            val methodListPtr = it.get()
            val count = methodListPtr.getInt(Native.POINTER_SIZE.toLong())
            if (count == 0) {
                listOf()
            } else {
                val firstMethod = TankerVerificationMethod(methodListPtr.getPointer(0))

                @Suppress("UNCHECKED_CAST")
                val out = (firstMethod.toArray(count) as Array<TankerVerificationMethod>).map(::verificationMethodFromCVerification).toList()
                lib.tanker_free_verification_method_list(methodListPtr)
                out
            }
        })
    }

    /**
     * Sets-up or updates verification methods for the user.
     * Must be called on an already opened Session.
     * @return A future that resolves if the operation succeeds
     */
    fun setVerificationMethod(verification: Verification): TankerFuture<Unit> {
        return TankerFuture(
                lib.tanker_set_verification_method(
                        tanker,
                        verification.toCVerification()
                ),
                Unit::class.java
        )
    }

    /**
     * Encrypts clear {@code data}.
     * @return A future that resolves when the data has been encrypted and shared.
     */
    fun encrypt(data: ByteArray): TankerFuture<ByteArray> {
        return encrypt(data, null)
    }

    /**
     * Encrypts clear {@code data} with options.
     * @return A future that resolves when the data has been encrypted and shared.
     */
    fun encrypt(data: ByteArray, options: EncryptionOptions?): TankerFuture<ByteArray> {
        val inBuf = Memory(data.size.toLong())
        inBuf.write(0, data, 0, data.size)

        val encryptedSize = lib.tanker_encrypted_size(data.size.toLong())
        val outBuf = Memory(encryptedSize)

        val futurePtr = lib.tanker_encrypt(tanker, outBuf, inBuf, data.size.toLong(), options)
        return TankerFuture<Unit>(futurePtr, Unit::class.java).andThen(TankerCallbackWithKeepAlive(keepAlive = inBuf) {
            outBuf.getByteArray(0, encryptedSize.toInt())
        })
    }

    fun encrypt(channel: TankerAsynchronousByteChannel, options: EncryptionOptions?): TankerFuture<TankerAsynchronousByteChannel> {
        val cb = TankerStreamInputSourceCallback(channel)
        val futurePtr = lib.tanker_stream_encrypt(tanker, cb, null, options)
        return TankerFuture<Pointer>(futurePtr, Pointer::class.java).andThen(TankerCallback {
            TankerStream(it, cb)
        })
    }

    fun encrypt(channel: TankerAsynchronousByteChannel): TankerFuture<TankerAsynchronousByteChannel> {
        return encrypt(channel, null)
    }

    fun decrypt(channel: TankerAsynchronousByteChannel): TankerFuture<TankerAsynchronousByteChannel> {
        val cb = TankerStreamInputSourceCallback(channel)
        val futurePtr = lib.tanker_stream_decrypt(tanker, cb, null)
        return TankerFuture<Pointer>(futurePtr, Pointer::class.java).andThen(TankerCallback {
            TankerStream(it, cb)
        })
    }

    /**
     * Decrypts {@code data} with options, assuming the data was encrypted and shared beforehand.
     * @return A future that resolves when the data has been decrypted.
     */
    fun decrypt(data: ByteArray): TankerFuture<ByteArray> {
        val inBuf = Memory(data.size.toLong())
        inBuf.write(0, data, 0, data.size)

        val plainSizeFut = TankerFuture<Pointer>(lib.tanker_decrypted_size(inBuf, data.size.toLong()), Pointer::class.java)
        val plainSize = try {
            Pointer.nativeValue(plainSizeFut.get())
        } catch (_: Throwable) {
            return plainSizeFut.transmute(ByteArray::class.java)
        }

        val outBuf = Memory(plainSize)
        val futurePtr = lib.tanker_decrypt(tanker, outBuf, inBuf, data.size.toLong())
        return TankerFuture<Unit>(futurePtr, Unit::class.java).andThen(TankerCallbackWithKeepAlive(keepAlive = inBuf) {
            outBuf.getByteArray(0, plainSize.toInt())
        })
    }

    /**
     * Get the resource ID used for sharing encrypted data.
     * @param data Data encrypted with {@code encrypt}.
     * @return The resource ID of the encrypted data (base64 encoded).
     */
    fun getResourceID(data: ByteArray): String {
        val inBuf = Memory(data.size.toLong())
        inBuf.write(0, data, 0, data.size)

        val future = lib.tanker_get_resource_id(inBuf, data.size.toLong())
        val outStringPtr = TankerFuture<Pointer>(future, Pointer::class.java).get()
        val outString = outStringPtr.getString(0, "UTF-8")
        lib.tanker_free_buffer(outStringPtr)
        return outString
    }

    /**
     * Get the resource ID used for sharing encrypted data.
     * @param channel Tanker channel returned either by {@code encrypt} or {@code decrypt}.
     * @return The resource ID of the encrypted data (base64 encoded).
     */
    fun getResourceID(channel: TankerAsynchronousByteChannel): String {
        return (channel as TankerStream).resourceID
    }

    /**
     * Shares the key for an encrypted resource with another Tanker user.
     * @param resourceIDs The IDs of the encrypted resources to share (base64 encoded each).
     * @param sharingOptions Specifies options like the users and groups to share with.
     * @return A future that resolves when the share is complete.
     */
    fun share(resourceIDs: Array<String>, sharingOptions: SharingOptions): TankerFuture<Unit> {
        val fut = lib.tanker_share(tanker, StringArray(resourceIDs), resourceIDs.size.toLong(), sharingOptions)
        return TankerFuture(fut, Unit::class.java)
    }

    /**
     * Create a group with the given members
     * @return A future of the group ID
     */
    fun createGroup(vararg memberPublicIdentities: String): TankerFuture<String> {
        val fut = lib.tanker_create_group(tanker, StringArray(memberPublicIdentities), memberPublicIdentities.size.toLong())
        return TankerFuture<Pointer>(fut, Pointer::class.java).then(TankerCallback {
            it.getError()?.let { throw it }
            val ptr = it.get()
            val str = ptr.getString(0)
            lib.tanker_free_buffer(ptr)
            str
        })
    }

    /**
     * Update the members of an existing group, referenced by its group ID.
     * @return A future that resolves when the operation completes.
     */
    fun updateGroupMembers(groupId: String, usersToAdd: Array<String>): TankerFuture<Unit> {
        val fut = lib.tanker_update_group_members(tanker, groupId, StringArray(usersToAdd), usersToAdd.size.toLong())
        return TankerFuture(fut, Unit::class.java)
    }

    /**
     * Subscribes to the "Device Revoked" Tanker event.
     * @param eventCallback The function to call when the event happens.
     * @return A connection, which can be passed to disconnectEvent.
     */
    @Deprecated("The deviceRevoked event is deprecated, it will be removed in the future")
    fun connectDeviceRevokedHandler(eventCallback: TankerDeviceRevokedHandler) {
        deviceRevokedHandlers.add(eventCallback)
    }

    private fun triggerDeviceRevokedEvent() {
        for (handler in deviceRevokedHandlers)
            try {
                handler.call()
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Callback has thrown an exception", e)
            }
    }

    /**
     * Unsubscribes from a Tanker event.
     */
    fun disconnectHandler(handler: Any) {
        deviceRevokedHandlers.remove(handler)
    }

    @Deprecated("Use createEncryptionSession(EncryptionOptions) instead")
    fun createEncryptionSession(sharingOptions: SharingOptions): TankerFuture<EncryptionSession> {
        val encryptionOptions = EncryptionOptions()
        encryptionOptions.shareWithUsers = sharingOptions.shareWithUsers
        encryptionOptions.nbUsers = sharingOptions.nbUsers
        encryptionOptions.shareWithGroups = sharingOptions.shareWithGroups
        encryptionOptions.nbGroups = sharingOptions.nbGroups

        return createEncryptionSession(encryptionOptions)
    }

    /**
     * Create an encryption session that will allow doing multiple encryption operations
     * with a reduced number of keys.
     */
    fun createEncryptionSession(encryptionOptions: EncryptionOptions): TankerFuture<EncryptionSession> {
        val fut = lib.tanker_encryption_session_open(tanker, encryptionOptions)
        return TankerFuture<Pointer>(fut, Pointer::class.java).then(TankerCallback {
            it.getError()?.let { throw it }
            val csession = it.get()
            EncryptionSession(csession)
        })
    }
}
