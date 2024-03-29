package io.tanker.api.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.jna.Pointer
import io.tanker.api.TankerFuture
import io.tanker.bindings.TankerLib
import io.tanker.api.TankerCallback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class TankerApp(private val client: OkHttpClient, private val verificationApiToken: String, private val url: String, val id: String, val privateKey: String) {
    fun getEmailVerificationCode(email: String): String {
        val jsonMapper = ObjectMapper()
        val reqJson = jsonMapper.createObjectNode()
        reqJson.put("app_id", id)
        reqJson.put("auth_token", verificationApiToken)
        reqJson.put("email", email)

        val request = Request.Builder()
                .url("$url/verification/email/code")
                .method("POST", reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val jsonResponse = jsonMapper.readTree(response.body?.string())
            return jsonResponse.get("verification_code").asText()
        }
    }

    fun getSMSVerificationCode(phoneNumber: String): String {
        val jsonMapper = ObjectMapper()
        val reqJson = jsonMapper.createObjectNode()
        reqJson.put("app_id", id)
        reqJson.put("auth_token", verificationApiToken)
        reqJson.put("phone_number", phoneNumber)

        val request = Request.Builder()
                .url("$url/verification/sms/code")
                .method("POST", reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val jsonResponse = jsonMapper.readTree(response.body?.string())
            return jsonResponse.get("verification_code").asText()
        }
    }
}
