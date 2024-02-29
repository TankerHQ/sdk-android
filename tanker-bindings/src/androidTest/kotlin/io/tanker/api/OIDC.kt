package io.tanker.api

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Base64

fun getIDToken(oidcConfig: ConfigOIDC) : String {
    val martineConfig = oidcConfig.users.getValue("martine")

    // Get a fresh OIDC ID token from GOOG
    val jsonMapper = ObjectMapper()
    val jsonObj = jsonMapper.createObjectNode()
    jsonObj.put("grant_type", "refresh_token")
    jsonObj.put("refresh_token", martineConfig.refreshToken)
    jsonObj.put("client_id", oidcConfig.clientId)
    jsonObj.put("client_secret", oidcConfig.clientSecret)
    val jsonBody = jsonMapper.writeValueAsString(jsonObj)

    val request = Request.Builder()
        .url("https://www.googleapis.com/oauth2/v4/token")
        .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()!!))
        .build()
    val response = OkHttpClient().newCall(request).execute()
    if (!response.isSuccessful)
        throw java.lang.RuntimeException("Google OAuth test request failed!")
    val jsonResponse = jsonMapper.readTree(response.body?.string())
    val oidcIdToken = jsonResponse.get("id_token").asText()

    return oidcIdToken
}

fun extractSubject(idToken: String) : String {
    val jwtBody = idToken.split(".")[1]
    val body = String(Base64.decode(jwtBody, Base64.URL_SAFE + Base64.NO_PADDING))
    val jsonBody = ObjectMapper().readTree(body)
    return jsonBody.get("sub").asText()
}
