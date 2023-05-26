package io.tanker.api.admin

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private fun toUrlSafeAppId(appId: String) = appId.replace('/', '_').replace('+', '-').trimEnd('=')

class Admin(private val appManagementUrl: String, private val appManagementToken: String, private val apiUrl: String, private val environmentName: String, private val verificationApiToken: String) {
    private val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                        .addHeader("accept", "application/json")
                        .addHeader("authorization", "Bearer $appManagementToken")
                        .build()
                chain.proceed(req)
            }
            .build()

    fun createApp(name: String): TankerApp {
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
            return TankerApp(client, verificationApiToken, apiUrl, jsonApp.get("id").asText(), jsonApp.get("private_signature_key").asText())
        }
    }

    fun deleteApp(appId: String) {
        val request = Request.Builder()
                .url("$appManagementUrl/v1/apps/${toUrlSafeAppId(appId)}")
                .method("DELETE", null)
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }
    }

    /**
     * Updates the app properties
     */
    fun appUpdate(appId: String, options: TankerAppUpdateOptions) {
        val jsonMapper = ObjectMapper()
        val reqJson = jsonMapper.createObjectNode()
        if (options.oidcClientId != null)
            reqJson.put("oidc_client_id", options.oidcClientId)
        if (options.oidcClientProvider != null)
            reqJson.put("oidc_provider", options.oidcClientProvider)
        if (options.userEnrollment != null)
            reqJson.put("enroll_users_enabled", options.userEnrollment!!)

        val request = Request.Builder()
                .url("$appManagementUrl/v1/apps/${toUrlSafeAppId(appId)}")
                .method("PATCH", reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }
    }
}
