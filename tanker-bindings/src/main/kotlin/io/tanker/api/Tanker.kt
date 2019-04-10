package io.tanker.api

import android.util.Log
import com.sun.jna.Memory
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

        private val lib = TankerLib.create()
        private var logCallbackLifeSupport: TankerLib.LogHandlerCallback? = null

        /**
         * @return The version string of the native Tanker SDK.
         */
         @JvmStatic fun getVersionString(): String {
             return TANKER_ANDROID_VERSION
         }

        @JvmStatic fun getNativeVersionString(): String {
            return lib.tanker_version_string()
        }

        /**
         * Sets the global tanker log handler.
         * Log messages generated by the Tanker SDK will go through this handler.
         */
        @JvmStatic fun setLogHandler(handler: TankerLib.LogHandlerCallback) {
            logCallbackLifeSupport = handler
            lib.tanker_set_log_handler(handler)
        }
    }
    private val tanker: Pointer
    private var eventCallbackLifeSupport = HashMap<Pointer, TankerLib.EventCallback>()

    init {
        lib.tanker_init()

        if (KVMx86Bug.hasBug()) {
            Log.w("io.tanker.sdk", "Warning: The tanker SDK detected that it is running on an x86 emulator with KVM enabled.\n"
                    +"A hardware-acceleration bug in some versions of the x86_32 Android emulator causes it to crash when starting Tanker.\n"
                    +"If you encounter a crash only in the x86 emulator, please make sure to update Android Studio (or use an x86_64 emulator)")
        }

        val createFuture = lib.tanker_create(tankerOptions)
        tanker = TankerFuture<Pointer>(createFuture, Pointer::class.java).get()
    }

    @Suppress("ProtectedInFinal", "Unused") protected fun finalize() {
        lib.tanker_destroy(tanker)
    }

    /**
     * Creates a Tanker account and opens a session
     * @param identity The opaque Tanker identity object generated on your application's server
     * @param authenticationMethods The authentication methods to register for identity verification
     * @return A future that resolves when the session is open (or closed in case an error occurred or the device was revoked).
     */
    fun signUp(identity: String, authenticationMethods: TankerAuthenticationMethods? = null): TankerFuture<Unit> {
        val futurePtr = lib.tanker_sign_up(tanker, identity, authenticationMethods)
        return TankerFuture(futurePtr, Unit::class.java)
    }

    /**
     * Tries to open a Tanker session and returns a status code
     * @param identity The opaque Tanker identity object generated on your application's server
     * @param signInOptions Must be passed in when identity verification is required
     * @return A future that resolves with a SignInResult
     */
    fun signIn(identity: String, signInOptions: TankerSignInOptions? = null): TankerFuture<TankerSignInResult> {
        val futurePtr = lib.tanker_sign_in(tanker, identity, signInOptions)
        return TankerFuture<Int>(futurePtr, Int::class.java).andThen(TankerCallback {
            TankerSignInResult.fromInt(it)
        })
    }

    /**
     * Closes a Tanker session.
     * @return A future that resolves when the session is closed.
     */
    fun signOut(): TankerFuture<Unit> {
        val futurePtr = lib.tanker_sign_out(tanker)
        return TankerFuture(futurePtr, Unit::class.java)
    }

    /**
     * Get whether the Tanker session is open.
     */
    fun isOpen(): Boolean {
        return lib.tanker_is_open(tanker)
    }

    /**
     * Gets the current device's ID as a string
     */
    fun getDeviceId(): TankerFuture<String> {
        val fut = TankerFuture<Pointer>(lib.tanker_device_id(tanker), Pointer::class.java)
        return fut.then(TankerCallback{
            val ptr = it.get()
            val str = ptr.getString(0)
            lib.tanker_free_buffer(ptr)
            str
        })
    }

    /**
     * Gets the list of the user's devices
     */
    fun getDeviceList(): TankerFuture<List<TankerDeviceInfo>> {
        val fut = TankerFuture<Pointer>(lib.tanker_get_device_list(tanker), Pointer::class.java)
        return fut.then(TankerCallback{
            val devListPtr = it.get()
            val count = devListPtr.getInt(0)
            val finalizer = TankerDeviceListFinalizer(lib, devListPtr)
            val firstDevice = TankerDeviceInfo(devListPtr.getPointer(Int.SIZE_BYTES.toLong()))
            @Suppress("UNCHECKED_CAST")
            val devices = firstDevice.toArray(count) as Array<TankerDeviceInfo>
            for (device in devices)
                device.finalizer = finalizer
            devices.toList()
        })
    }

    /**
     * Revoke a device by device id.
     */
    fun revokeDevice(deviceId: String): TankerFuture<Unit> {
        return TankerFuture(lib.tanker_revoke_device(tanker, deviceId), Unit::class.java)
    }

    /**
     * Generates and registers an unlock key that can be used to accept a device.
     * @return The unlock key.
    */
    fun generateAndRegisterUnlockKey(): TankerFuture<String> {
        val fut = TankerFuture<Pointer>(lib.tanker_generate_and_register_unlock_key(tanker), Pointer::class.java)
        return fut.then(TankerCallback {
            val ptr = it.get()
            val str = ptr.getString(0)
            lib.tanker_free_buffer(ptr)
            str
        })
    }

    /**
     * Checks whether an unlock key is set up for the current user.
     * NOTE: This is a low-level function, only needed if you called generateAndRegisterUnlockKey
     *       You probably want to use hasRegisteredUnlockMethods instead.
     * Must be called on an already opened Session.
     * @return A future of whether the unlock has been setup.
     */
    fun isUnlockAlreadySetUp(): TankerFuture<Boolean> {
        val fut = TankerFuture<Pointer>(lib.tanker_is_unlock_already_set_up(tanker), Pointer::class.java)
        return fut.andThen(TankerCallback {
            Pointer.nativeValue(it) != 0L
        })
    }

    /**
     * Checks if any unlock methods has been registered for the current user.
     * Must be called on an already opened Session.
     */
    fun hasRegisteredUnlockMethods(): Boolean {
        val cfut = lib.tanker_has_registered_unlock_methods(tanker)
        return TankerFuture<Boolean>(cfut, Boolean::class.java).get()
    }

    /**
     * Checks if the given unlock method has been registered for the current user.
     * Must be called on an already opened Session.
     */
    fun hasRegisteredUnlockMethod(unlockMethod: TankerUnlockMethod): Boolean {
        val cfut = lib.tanker_has_registered_unlock_method(tanker, unlockMethod)
        return TankerFuture<Boolean>(cfut, Boolean::class.java).get()
    }

    /**
     * Returns the list of currently registered unlock methods.
     * Must be called on an already opened Session.
     * @return Ordered list of unlock methods that are currently set-up.
     */
    fun registeredUnlockMethods(): List<TankerUnlockMethodInfo> {
        val cfut = lib.tanker_registered_unlock_methods(tanker);
        val flags = Pointer.nativeValue(TankerFuture<Pointer>(cfut, Pointer::class.java).get()).toInt()

        // Unpack bitflag into values
        val components = ArrayList<TankerUnlockMethodInfo>()
        for (value in TankerUnlockMethod.values())
            if (flags and value.value == value.value)
                components.add(TankerUnlockMethodInfo(value))
        return components
    }

    /**
     * Sets-up or updates unlock methods for the user.
     * Must be called on an already opened Session.
     * @return A future that resolves if the operation succeeds
     */
    fun registerUnlock(options: TankerUnlockOptions): TankerFuture<Unit> {
        return TankerFuture(
                lib.tanker_register_unlock(
                        tanker = tanker,
                        password = options.password,
                        email = options.email
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
    fun encrypt(data: ByteArray, options: TankerEncryptOptions?): TankerFuture<ByteArray> {
        val inBuf = Memory(data.size.toLong())
        inBuf.write(0, data, 0, data.size)

        val encryptedSize = lib.tanker_encrypted_size(data.size.toLong())
        val outBuf = Memory(encryptedSize)

        val futurePtr = lib.tanker_encrypt(tanker, outBuf, inBuf, data.size.toLong(), options)
        return TankerFuture<Unit>(futurePtr, Unit::class.java).andThen(TankerCallback {
            outBuf.getByteArray(0, encryptedSize.toInt())
        })
    }


    /**
     * Decrypts {@code data}, assuming the data was encrypted and shared beforehand.
     * @return A future that resolves when the data has been decrypted.
     */
    fun decrypt(data: ByteArray): TankerFuture<ByteArray> {
        return decrypt(data, null)
    }

    /**
     * Decrypts {@code data} with options, assuming the data was encrypted and shared beforehand.
     * @return A future that resolves when the data has been decrypted.
     */
    fun decrypt(data: ByteArray, decryptOptions: TankerDecryptOptions?): TankerFuture<ByteArray> {
        val inBuf = Memory(data.size.toLong())
        inBuf.write(0, data, 0, data.size)

        val plainSizeFut = TankerFuture<Pointer>(lib.tanker_decrypted_size(inBuf, data.size.toLong()), Pointer::class.java)
        val plainSize = try {
            Pointer.nativeValue(plainSizeFut.get())
        } catch (_: Throwable) {
            return plainSizeFut.transmute(ByteArray::class.java)
        }

        val outBuf = Memory(plainSize)

        val futurePtr = lib.tanker_decrypt(tanker, outBuf, inBuf, data.size.toLong(), decryptOptions)
        return TankerFuture<Unit>(futurePtr, Unit::class.java).andThen(TankerCallback {
            @Suppress("UNUSED_VARIABLE")
            val keepalive = inBuf
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
     * Shares the key for an encrypted resource with another Tanker user.
     * @param resourceIDs The IDs of the encrypted resources to share (base64 encoded each).
     * @param shareOptions Specifies options like the users and groups to share with.
     * @return A future that resolves when the share is complete.
     */
    fun share(resourceIDs: Array<String>, shareOptions: TankerShareOptions): TankerFuture<Unit> {
        val fut = lib.tanker_share(tanker, StringArray(shareOptions.recipientPublicIdentities), shareOptions.recipientPublicIdentities.size.toLong(),
                                    StringArray(shareOptions.recipientGids), shareOptions.recipientGids.size.toLong(),
                                    StringArray(resourceIDs), resourceIDs.size.toLong())
        return TankerFuture(fut, Unit::class.java)
    }

    /**
     * Create a group with the given members
     * @return A future of the group ID
     */
    fun createGroup(vararg memberPublicIdentities: String): TankerFuture<String>
    {
        val fut = lib.tanker_create_group(tanker, StringArray(memberPublicIdentities), memberPublicIdentities.size.toLong())
        return TankerFuture<Pointer>(fut, Pointer::class.java).then(TankerCallback{
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
    fun updateGroupMembers(groupId: String, usersToAdd: Array<String>): TankerFuture<Unit>
    {
        val fut = lib.tanker_update_group_members(tanker, groupId, StringArray(usersToAdd), usersToAdd.size.toLong())
        return TankerFuture(fut, Unit::class.java)
    }

    private fun connectGenericHandler(cb: (Pointer?) -> Unit, event: TankerEvent): TankerConnection {
        // We don't want to let the user run code directly on the callback thread,
        // because blocking in the callback prevents progress and could deadlock us.
        // Instead we wrap in a future to move the handler to another thread, and run asynchronously
        val moveThreadCPromise = lib.tanker_promise_create()
        val moveThreadCFuture = lib.tanker_promise_get_future(moveThreadCPromise)
        val moveThreadFuture = TankerFuture<Unit>(moveThreadCFuture, Unit::class.java)
        lib.tanker_promise_set_value(moveThreadCPromise, Pointer(0))

        val callbackWrapper = object : TankerLib.EventCallback {
            override fun callback(arg: Pointer?) {
                moveThreadFuture.then(TankerVoidCallback {
                    try {
                        cb(arg)
                    } catch (e: Throwable) {
                        Log.e(LOG_TAG, "Caught exception in event handler:", e)
                    }
                })
            }
        }
        val fut = lib.tanker_event_connect(tanker, event, callbackWrapper, Pointer(0))
        return TankerFuture<ConnectionPointer>(fut, ConnectionPointer::class.java).then<TankerConnection>(TankerCallback {
            val connection = it.get()
            eventCallbackLifeSupport[connection] = callbackWrapper
            TankerConnection(connection)
        }).get()
    }

    /**
     * Subscribes to the "Session Closed" Tanker event.
     * @param callback The function to call when the event happens.
     * @return A connection future, whose result can be passed to disconnectEvent.
     */
    fun connectSessionClosedHandler(eventCallback: TankerSessionClosedHandler): TankerConnection {
        return connectGenericHandler({eventCallback.call()}, TankerEvent.SESSION_CLOSED)
    }

    /**
     * Subscribes to the "Device Created" Tanker event.
     * @param callback The function to call when the event happens.
     * @return A connection, which can be passed to disconnectEvent.
     */
    fun connectDeviceCreatedHandler(eventCallback: TankerDeviceCreatedHandler): TankerConnection {
        return connectGenericHandler({eventCallback.call()}, TankerEvent.DEVICE_CREATED)
    }

    /**
     * Subscribes to the "Device Revoked" Tanker event.
     * @param callback The function to call when the event happens.
     * @return A connection, which can be passed to disconnectEvent.
     */
    fun connectDeviceRevokedHandler(eventCallback: TankerDeviceRevokedHandler): TankerConnection {
        return connectGenericHandler({eventCallback.call()}, TankerEvent.DEVICE_REVOKED)
    }

    /**
     * Unsubscribes from a Tanker event.
     * @see eventConnect
     */
    fun eventDisconnect(connection: TankerConnection) {
        if (eventCallbackLifeSupport.remove(connection.value) == null)
            throw IllegalArgumentException("Trying to disconnect an invalid event connection")
        val fut = lib.tanker_event_disconnect(tanker, connection.value)
        TankerFuture<Unit>(fut, Unit::class.java).get()
    }
}
