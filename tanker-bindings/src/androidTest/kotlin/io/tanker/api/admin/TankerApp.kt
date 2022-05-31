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

class TankerApp(private val client: OkHttpClient, private val url: String, val id: String, val authToken: String, val privateKey: String) {
    companion object {
        private val lib = AdminLib.create()
        private val tankerlib = TankerLib.create()
    }

    fun getEmailVerificationCode(email: String): TankerFuture<String> {
        val jsonMapper = ObjectMapper()
        val reqJson = jsonMapper.createObjectNode()
        reqJson.put("app_id", id)
        reqJson.put("auth_token", authToken)
        reqJson.put("email", email)

        val request = Request.Builder()
                .url("$url/verification/email/code")
                .method("POST", reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val jsonResponse = jsonMapper.readTree(response.body?.string())
            return TankerFuture<Unit>(lib).then<String> { return@then jsonResponse.get("verification_code").asText() }
        }
    }

    fun getSMSVerificationCode(phoneNumber: String): TankerFuture<String> {
        val jsonMapper = ObjectMapper()
        val reqJson = jsonMapper.createObjectNode()
        reqJson.put("app_id", id)
        reqJson.put("auth_token", authToken)
        reqJson.put("phone_number", phoneNumber)

        val request = Request.Builder()
                .url("$url/verification/sms/code")
                .method("POST", reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val jsonResponse = jsonMapper.readTree(response.body?.string())
            return TankerFuture<Unit>(lib).then<String> { return@then jsonResponse.get("verification_code").asText() }
        }
    }
}

