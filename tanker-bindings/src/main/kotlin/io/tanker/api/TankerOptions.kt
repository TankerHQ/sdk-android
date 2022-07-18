package io.tanker.api

import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.tanker.bindings.DatastoreLib
import io.tanker.bindings.TankerLib
import io.tanker.datastore.DatastoreOptions

class HttpOptions : Structure() {
    @JvmField var httpSendRequest: TankerLib.HttpSendRequestCallback? = null
    @JvmField var httpCancelRequest: TankerLib.HttpCancelRequestCallback? = null
    @JvmField var httpData: Pointer? = Pointer.NULL

    override fun getFieldOrder() =
        listOf(
            "httpSendRequest",
            "httpCancelRequest",
            "httpData",
        )
}

/**
 * Options that can be given when opening a TankerSession
 *
 * Please use the builder interface and the setter methods instead of accessing the fields directly.
 * Those are not part of the public API and are subject to change.
 */
class TankerOptions : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 4
    @JvmField var appId: String? = null
    @JvmField var url: String? = null
    @JvmField var persistentPath: String? = null
    @JvmField var sdkType: String = "client-android"
    @JvmField var sdkVersion: String = "dev"

    @JvmField internal var httpOptions: HttpOptions = HttpOptions()

    @JvmField var cachePath: String? = null
    @JvmField internal var datastoreOptions: DatastoreOptions = DatastoreOptions()

    /**
     * Mandatory. Sets the app to use for the TankerSession.
     */
    fun setAppId(appId: String): TankerOptions {
        this.appId = appId
        return this
    }

    /**
     * Optional. Sets the dedicated environment to use.
     */
    fun setUrl(url: String): TankerOptions {
        this.url = url
        return this
    }

    internal fun setSdkType(sdkType: String): TankerOptions {
        this.sdkType = sdkType
        return this
    }

    /**
     * Mandatory. The path on disk where the Tanker SDK will save private data and key material.
     */
    fun setPersistentPath(persistentPath: String): TankerOptions {
        this.persistentPath = persistentPath
        return this
    }

    /**
     * Mandatory. The path on disk where the Tanker SDK will save the encrypted cache.
     */
    fun setCachePath(cachePath: String): TankerOptions {
        this.cachePath = cachePath
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf(
            "version",
            "appId",
            "url",
            "persistentPath",
            "cachePath",
            "sdkType",
            "sdkVersion",
            "httpOptions",
            "datastoreOptions",
        )
    }
}
