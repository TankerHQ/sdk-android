package io.tanker.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.tanker.admin.TankerAppUpdateOptions
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test


class UnlockTests : TankerSpec() {
    private lateinit var identity: String
    private lateinit var tanker1: Tanker
    private lateinit var tanker2: Tanker

    @Before
    fun beforeTest() {
        identity = tc.createIdentity()
        tanker1 = Tanker(options.setWritablePath(createTmpDir().toString()))
        tanker2 = Tanker(options.setWritablePath(createTmpDir().toString()))
    }

    private fun checkSessionToken(publicIdentity: String, token: String, allowedMethod: String): String {
        val jsonMapper = ObjectMapper()
        val jsonAllowedMethod = jsonMapper.createObjectNode()
        jsonAllowedMethod.put("type", allowedMethod)
        val jsonAllowedMethods = jsonMapper.createArrayNode()
        jsonAllowedMethods.add(jsonAllowedMethod)
        val jsonObj = jsonMapper.createObjectNode()
        jsonObj.put("app_id", tc.id())
        jsonObj.put("auth_token", tc.authToken())
        jsonObj.put("public_identity", publicIdentity)
        jsonObj.put("session_token", token)
        jsonObj.set<JsonNode>("allowed_methods", jsonAllowedMethods)
        val jsonBody = jsonMapper.writeValueAsString(jsonObj)

        val url = tc.trustchaindUrl()
        val request = Request.Builder()
                .url("$url/verification/session-token")
                .post(jsonBody.toRequestBody(HttpClient.JSON))
                .build()
        val response = OkHttpClient().newCall(request).execute()
        if (!response.isSuccessful)
            throw RuntimeException("Check session token request failed: "+response.body?.string())
        val jsonResponse = jsonMapper.readTree(response.body?.string())
        return jsonResponse.get("verification_method").asText()
    }

    @Test
    fun can_validate_a_new_device_using_a_verification_key() {
        tanker1.start(identity).get()
        val verificationKey = tanker1.generateVerificationKey().get()
        tanker1.registerIdentity(VerificationKeyVerification(verificationKey)).get()
        tanker1.stop().get()
        tanker2.start(identity).get()
        tanker2.verifyIdentity(VerificationKeyVerification(verificationKey)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)
        tanker2.stop().get()
    }

    @Test
    fun can_setup_and_use_an_unlock_password() {
        val pass = "this is a password"

        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(pass)).get()
        tanker1.stop().get()

        tanker2.start(identity).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.IDENTITY_VERIFICATION_NEEDED)
        tanker2.verifyIdentity(PassphraseVerification(pass)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)
        tanker2.stop().get()
    }

    @Test
    fun can_update_the_unlock_password() {
        val oldpass = "This is an old password"
        val newpass = "This is a new password"

        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(oldpass)).get()
        tanker1.setVerificationMethod(PassphraseVerification(newpass)).get()
        tanker1.stop().get()

        tanker2.start(identity).get()
        tanker2.verifyIdentity(PassphraseVerification(newpass)).get()
        tanker2.stop().get()
    }

    @Test
    fun alice_s_second_device_can_decrypt_old_resources() {
        val pass = "This is a strong password"
        val plainText = "plain text"

        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(pass)).get()
        val secret = tanker1.encrypt(plainText.toByteArray()).get()
        tanker1.stop().get()

        tanker2.start(identity).get()
        tanker2.verifyIdentity(PassphraseVerification(pass)).get()
        assertThat(String(tanker2.decrypt(secret).get())).isEqualTo(plainText)
        tanker2.stop().get()
    }

    @Test
    fun can_check_that_the_password_unlock_method_is_set_up() {
        val pass = "this is a password"

        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(pass)).get()
        val methods = tanker1.getVerificationMethods().get()
        assertThat(methods).containsExactlyInAnyOrderElementsOf(listOf(PassphraseVerificationMethod))
        tanker1.stop().get()
    }

    @Test
    fun can_check_that_the_email_unlock_method_is_set_up() {
        val email = "bob@tanker.io"

        tanker1.start(identity).get()
        val verificationCode = tc.getEmailVerificationCode(email)
        tanker1.registerIdentity(EmailVerification(email, verificationCode)).get()
        val methods = tanker1.getVerificationMethods().get()
        assertThat(methods).containsExactlyInAnyOrderElementsOf(listOf(EmailVerificationMethod(email)))
        tanker1.stop().get()
    }

    @Test
    fun can_check_that_the_phone_number_unlock_method_is_set_up() {
        val phoneNumber = "+33600112233"

        tanker1.start(identity).get()
        val verificationCode = tc.getSMSVerificationCode(phoneNumber)
        tanker1.registerIdentity(PhoneNumberVerification(phoneNumber, verificationCode)).get()
        val methods = tanker1.getVerificationMethods().get()
        assertThat(methods).containsExactlyInAnyOrderElementsOf(listOf(PhoneNumberVerificationMethod(phoneNumber)))
        tanker1.stop().get()
    }

    @Test
    fun can_get_the_list_of_unlock_methods_that_have_been_set_up() {
        val pass = "this is a password"
        val email = "bob@tanker.io"

        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(pass)).get()
        assertThat(tanker1.getVerificationMethods().get().size).isEqualTo(1)
        val verificationCode = tc.getEmailVerificationCode(email)
        tanker1.setVerificationMethod(EmailVerification(email, verificationCode)).get()
        val methods = tanker1.getVerificationMethods().get()
        assertThat(methods.size).isEqualTo(2)
        tanker1.stop().get()
    }

    @Test
    fun can_use_setVerificationMethod_to_setup_and_update_an_unlock_method() {
        val email = "bob@tanker.io"
        val oldpass = "this is an old password"
        val newpass = "this is a new password"

        tanker1.start(identity).get()
        val verificationCode = tc.getEmailVerificationCode(email)
        tanker1.registerIdentity(EmailVerification(email, verificationCode)).get()
        assertThat(tanker1.getVerificationMethods().get().size).isEqualTo(1)
        tanker1.setVerificationMethod(PassphraseVerification(oldpass)).get()
        tanker1.setVerificationMethod(PassphraseVerification(newpass)).get()
        assertThat(tanker1.getVerificationMethods().get().size).isEqualTo(2)
        tanker1.stop().get()

        tanker2.start(identity).get()
        tanker2.verifyIdentity(PassphraseVerification(newpass)).get()
        tanker2.stop().get()
    }

    @Test
    fun can_unlock_with_a_verification_code() {
        val email = "bob@tanker.io"

        tanker1.start(identity).get()
        var verificationCode = tc.getEmailVerificationCode(email)
        tanker1.registerIdentity(EmailVerification(email, verificationCode)).get()

        tanker2.start(identity).get()
        verificationCode = tc.getEmailVerificationCode(email)
        tanker2.verifyIdentity(EmailVerification(email, verificationCode)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)

        tanker1.stop().get()
        tanker2.stop().get()
    }

    @Test
    fun can_use_OIDC_ID_Tokens_as_verification() {
        val oidcConfig = Config.getOIDCConfig()
        val martineConfig = oidcConfig.users.getValue("martine")
        val martineIdentity = tc.createIdentity(martineConfig.email)

        val appOptions = TankerAppUpdateOptions()
                .setOidcClientId(oidcConfig.clientId)
                .setOidcClientProvider(oidcConfig.provider)
        tc.admin.appUpdate(tc.id(), appOptions).get()

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

        // Check that we can use our ID token as a verification method
        tanker1.start(martineIdentity).get()
        tanker1.registerIdentity(OIDCIDTokenVerification(oidcIdToken)).get()
        tanker1.stop().get()

        tanker2.start(martineIdentity).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.IDENTITY_VERIFICATION_NEEDED)
        tanker2.verifyIdentity(OIDCIDTokenVerification(oidcIdToken)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)

        val methods = tanker2.getVerificationMethods().get()
        assert(methods[0] is OIDCIDTokenVerificationMethod)

        tanker2.stop().get()
    }

    @Test
    fun can_get_a_session_token_with_registerIdentity() {
        val passphrase = "Offline Last Seen Mar 3rd, 2018"

        val appOptions = TankerAppUpdateOptions().setSessionCertificates(true)
        tc.admin.appUpdate(tc.id(), appOptions).get()

        tanker1.start(identity).get()
        val options = VerificationOptions().withSessionToken(true)
        val token = tanker1.registerIdentity(PassphraseVerification(passphrase), options).get()
        assertThat(token).isNotBlank()

        val publicIdentity = Identity.getPublicIdentity(identity)
        val expectedMethod = "passphrase"
        val usedMethod = checkSessionToken(publicIdentity, token!!, expectedMethod)
        assertThat(usedMethod).isEqualTo(expectedMethod)

        tanker1.stop().get()
    }

    @Test
    fun can_get_a_session_token_with_verifyIdentity() {
        val passphrase = "Offline Last Seen Mar 3rd, 2018"

        val appOptions = TankerAppUpdateOptions().setSessionCertificates(true)
        tc.admin.appUpdate(tc.id(), appOptions).get()

        tanker1.start(identity).get()
        val options = VerificationOptions().withSessionToken(true)
        val notToken = tanker1.registerIdentity(PassphraseVerification(passphrase)).get()
        assertThat(notToken).isNull()
        val token = tanker1.verifyIdentity(PassphraseVerification(passphrase), options).get()
        assertThat(token).isNotBlank()

        val publicIdentity = Identity.getPublicIdentity(identity)
        val expectedMethod = "passphrase"
        val usedMethod = checkSessionToken(publicIdentity, token!!, expectedMethod)
        assertThat(usedMethod).isEqualTo(expectedMethod)

        tanker1.stop().get()
    }

    @Test
    fun can_get_a_session_token_with_setVerificationMethod() {
        val pass1 = "PassOne"
        val pass2 = "PassTwo"

        val appOptions = TankerAppUpdateOptions().setSessionCertificates(true)
        tc.admin.appUpdate(tc.id(), appOptions).get()

        tanker1.start(identity).get()
        val options = VerificationOptions().withSessionToken(true)
        val notToken = tanker1.registerIdentity(PassphraseVerification(pass1)).get()
        assertThat(notToken).isNull()
        val token = tanker1.setVerificationMethod(PassphraseVerification(pass2), options).get()
        assertThat(token).isNotBlank()

        val publicIdentity = Identity.getPublicIdentity(identity)
        val expectedMethod = "passphrase"
        val usedMethod = checkSessionToken(publicIdentity, token!!, expectedMethod)
        assertThat(usedMethod).isEqualTo(expectedMethod)

        tanker1.stop().get()
    }
}
