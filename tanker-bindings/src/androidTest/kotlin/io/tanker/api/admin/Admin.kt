package io.tanker.api.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.jna.Pointer
import io.tanker.api.TankerFuture
import io.tanker.api.TankerVoidCallback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private fun toUrlSafeAppId(appId: String) = appId.replace('/', '_').replace('+', '-').trimEnd('=')

class Admin(private val appManagementUrl: String, private val appManagementToken: String, private val apiUrl: String, private val environmentName: String) {
    private var cadmin: Pointer? = null
    private val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                        .addHeader("authorization", "Bearer $appManagementToken")
                        .build()
                chain.proceed(req)
            }
            .build()

    companion object {
        private val lib = AdminLib.create()
    }


    @Suppress("ProtectedInFinal", "Unused")
    protected fun finalize() {
        if (cadmin != null) {
            lib.tanker_admin_destroy(cadmin!!)
        }
    }

    /**
     * Authenticate to the Tanker admin server API
     *
     * This must be called before doing any other operation
     */
    fun connect(): TankerFuture<Unit> {
        return TankerFuture<Pointer>(lib.tanker_admin_connect(appManagementUrl, appManagementToken, environmentName), Pointer::class.java, lib, keepAlive = this).andThen(TankerVoidCallback {
            cadmin = it
        })
    }

    fun createApp(name: String): TankerFuture<TankerApp> {
        val jsonMapper = ObjectMapper()
        val reqJson = jsonMapper.createObjectNode()
        reqJson.put("environment_name", environmentName)
        reqJson.put("name", name)

        val request = Request.Builder()
                .url("$appManagementUrl/v1/apps")
                .method("POST", reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val jsonResponse = jsonMapper.readTree(response.body?.string())
            val jsonApp = jsonResponse.get("app")
            return TankerFuture<Unit>(lib).then<TankerApp> { return@then TankerApp(client, apiUrl, jsonApp.get("id").asText(), jsonApp.get("auth_token").asText(), jsonApp.get("private_key").asText()) }
        }
    }

    fun deleteApp(appId: String): TankerFuture<Unit> {
        val request = Request.Builder()
                .url("$appManagementUrl/v1/apps/${toUrlSafeAppId(appId)}")
                .method("DELETE", null)
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return TankerFuture(lib)
        }
    }

    /**
     * Updates the app properties
     */
    fun appUpdate(appId: String, options: TankerAppUpdateOptions): TankerFuture<Unit> {
        val jsonMapper = ObjectMapper()
        val reqJson = jsonMapper.createObjectNode()
        if (options.oidcClientId != null)
            reqJson.put("oidc_client_id", options.oidcClientId)
        if (options.oidcClientProvider != null)
            reqJson.put("oidc_provider", options.oidcClientProvider)
        if (options.preverifiedVerification != null)
            reqJson.put("preverified_verification_enabled", options.preverifiedVerification?.value != 0.toByte())
        if (options.userEnrollment != null)
            reqJson.put("enroll_users_enabled", options.userEnrollment?.value != 0.toByte())

        val request = Request.Builder()
                .url("$appManagementUrl/v1/apps/${toUrlSafeAppId(appId)}")
                .method("PATCH", reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return TankerFuture(lib)
        }
    }
}
