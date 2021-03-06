package io.tanker.api

import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.tanker.bindings.TankerLib

/**
 * Options that can be given when opening a TankerSession
 *
 * Please use the builder interface and the setter methods instead of accessing the fields directly.
 * Those are not part of the public API and are subject to change.
 */
class TankerOptions : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var version: Byte = 3
    @JvmField var appId: String? = null
    @JvmField var url: String? = null
    @JvmField var writablePath: String? = null
    @JvmField var sdkType: String = "client-android"
    @JvmField var sdkVersion: String = "dev"
    @JvmField var httpSendRequest: TankerLib.HttpSendRequestCallback? = null
    @JvmField var httpCancelRequest: TankerLib.HttpCancelRequestCallback? = null
    @JvmField var httpData: Pointer? = Pointer.NULL

    /**
     * @deprecated use setAppId
     */
    fun setTrustchainId(trustchainId: String): TankerOptions {
        return setAppId(trustchainId)
    }

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
     * Mandatory. The path on disk where the Tanker SDK will save data and key material.
     */
    fun setWritablePath(writablePath: String): TankerOptions {
        this.writablePath = writablePath
        return this
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "appId", "url", "writablePath", "sdkType", "sdkVersion", "httpSendRequest", "httpCancelRequest", "httpData")
    }
}
