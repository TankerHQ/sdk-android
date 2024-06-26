package io.tanker.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.tanker.api.admin.OidcProviderConfig
import io.tanker.api.admin.TankerAppUpdateOptions
import io.tanker.api.errors.InvalidArgument
import io.tanker.api.errors.InvalidVerification
import io.tanker.api.errors.PreconditionFailed
import okhttp3.OkHttpClient
import okhttp3.Request
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
        tanker1 = Tanker(
            options.setPersistentPath(createTmpDir())
                .setCachePath(createTmpDir())
        )
        tanker2 = Tanker(
            options.setPersistentPath(createTmpDir())
                .setCachePath(createTmpDir())
        )
    }

    private fun checkSessionToken(
        publicIdentity: String,
        token: String,
        allowedMethod: String,
        value: String? = null
    ): String {
        val jsonMapper = ObjectMapper()
        val jsonAllowedMethod = jsonMapper.createObjectNode()
        jsonAllowedMethod.put("type", allowedMethod)
        if (value != null) {
            if (allowedMethod == "email") {
                jsonAllowedMethod.put("email", value)
            } else if (allowedMethod == "phone_number") {
                jsonAllowedMethod.put("phone_number", value)
            }
        }
        val jsonAllowedMethods = jsonMapper.createArrayNode()
        jsonAllowedMethods.add(jsonAllowedMethod)
        val jsonObj = jsonMapper.createObjectNode()
        jsonObj.put("app_id", tc.id())
        jsonObj.put("auth_token", tc.verificationApiToken())
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
            throw RuntimeException("Check session token request failed: " + response.body?.string())
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
        val phoneNumber = "+33639982233"

        tanker1.start(identity).get()
        val verificationCode = tc.getSMSVerificationCode(phoneNumber)
        tanker1.registerIdentity(PhoneNumberVerification(phoneNumber, verificationCode)).get()
        val methods = tanker1.getVerificationMethods().get()
        assertThat(methods).containsExactlyInAnyOrderElementsOf(
            listOf(
                PhoneNumberVerificationMethod(
                    phoneNumber
                )
            )
        )
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

        val oidcProviderConfig =
            OidcProviderConfig(oidcConfig.clientId, oidcConfig.displayName, oidcConfig.issuer)
        val appOptions = TankerAppUpdateOptions()
            .setOidcProvider(oidcProviderConfig)
        tc.admin.appUpdate(tc.id(), appOptions)

        val oidcIdToken = getIDToken(oidcConfig)

        // Check that we can use our ID token as a verification method
        val nonce = tanker1.createOidcNonce().get()
        tanker1.start(martineIdentity).get()
        tanker1.setOidcTestNonce(nonce).get()
        tanker1.registerIdentity(OIDCIDTokenVerification(oidcIdToken)).get()
        tanker1.stop().get()

        tanker2.start(martineIdentity).get()
        val nonce2 = tanker2.createOidcNonce().get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.IDENTITY_VERIFICATION_NEEDED)
        tanker2.setOidcTestNonce(nonce2).get()
        tanker2.verifyIdentity(OIDCIDTokenVerification(oidcIdToken)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)

        val methods = tanker2.getVerificationMethods().get()
        assert(methods[0] is OIDCIDTokenVerificationMethod)

        tanker2.stop().get()
    }

    @Test
    fun can_use_oidc_authorization_code_as_verification() {
        val fakeOidcIssuerUrl = Config.getOIDCConfig().fakeOidcIssuerUrl
        val oidcProviderConfig =
            OidcProviderConfig("tanker", "fake-oidc", fakeOidcIssuerUrl)
        val appOptions = TankerAppUpdateOptions()
            .setOidcProvider(oidcProviderConfig)
        val oidcProvider = tc.admin.appUpdate(tc.id(), appOptions)

        val providerID = oidcProvider.get("id").asText()
        val subjectCookie = "fake_oidc_subject=martine"


        tanker1.start(identity).get()

        val verification1 = tanker1.authenticateWithIDP(providerID, subjectCookie).get()
        val verification2 = tanker1.authenticateWithIDP(providerID, subjectCookie).get()

        tanker1.registerIdentity(verification1).get()

        tanker2.start(identity).get()
        tanker2.verifyIdentity(verification2).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)

        tanker1.stop().get()
        tanker2.stop().get()
    }

    @Test
    fun can_get_a_session_token_with_registerIdentity() {
        val passphrase = "Offline Last Seen Mar 3rd, 2018"

        tanker1.start(identity).get()
        val options = VerificationOptions().withSessionToken(true)
        val token = tanker1.registerIdentity(PassphraseVerification(passphrase), options).get()
        assertThat(token).isNotBlank

        val publicIdentity = Identity.getPublicIdentity(identity)
        val expectedMethod = "passphrase"
        val usedMethod = checkSessionToken(publicIdentity, token!!, expectedMethod)
        assertThat(usedMethod).isEqualTo(expectedMethod)

        tanker1.stop().get()
    }

    @Test
    fun can_get_a_session_token_with_verifyIdentity() {
        val passphrase = "Offline Last Seen Mar 3rd, 2018"

        tanker1.start(identity).get()
        val options = VerificationOptions().withSessionToken(true)
        val notToken = tanker1.registerIdentity(PassphraseVerification(passphrase)).get()
        assertThat(notToken).isNull()
        val token = tanker1.verifyIdentity(PassphraseVerification(passphrase), options).get()
        assertThat(token).isNotBlank

        val publicIdentity = Identity.getPublicIdentity(identity)
        val expectedMethod = "passphrase"
        val usedMethod = checkSessionToken(publicIdentity, token!!, expectedMethod)
        assertThat(usedMethod).isEqualTo(expectedMethod)

        tanker1.stop().get()
    }

    @Test
    fun can_get_a_session_token_with_setVerificationMethod_with_passphrase() {
        val pass1 = "PassOne"
        val pass2 = "PassTwo"

        tanker1.start(identity).get()
        val options = VerificationOptions().withSessionToken(true)
        val notToken = tanker1.registerIdentity(PassphraseVerification(pass1)).get()
        assertThat(notToken).isNull()
        val token = tanker1.setVerificationMethod(PassphraseVerification(pass2), options).get()
        assertThat(token).isNotBlank

        val publicIdentity = Identity.getPublicIdentity(identity)
        val expectedMethod = "passphrase"
        val usedMethod = checkSessionToken(publicIdentity, token!!, expectedMethod)
        assertThat(usedMethod).isEqualTo(expectedMethod)

        tanker1.stop().get()
    }

    @Test
    fun can_get_a_session_token_with_setVerificationMethod_with_email() {
        val pass = "PassOne"
        val email = "bob@tanker.io"

        tanker1.start(identity).get()
        val options = VerificationOptions().withSessionToken(true)
        val notToken = tanker1.registerIdentity(PassphraseVerification(pass)).get()
        assertThat(notToken).isNull()

        var verificationCode = tc.getEmailVerificationCode(email)
        val token =
            tanker1.setVerificationMethod(EmailVerification(email, verificationCode), options).get()
        assertThat(token).isNotBlank

        val publicIdentity = Identity.getPublicIdentity(identity)
        val expectedMethod = "email"
        val usedMethod = checkSessionToken(publicIdentity, token!!, expectedMethod, email)
        assertThat(usedMethod).isEqualTo(expectedMethod)

        tanker1.stop().get()
    }

    @Test
    fun can_get_a_session_token_with_setVerificationMethod_with_phone_number() {
        val pass = "PassOne"
        val phoneNumber = "+33639982233"

        tanker1.start(identity).get()
        val options = VerificationOptions().withSessionToken(true)
        val notToken = tanker1.registerIdentity(PassphraseVerification(pass)).get()
        assertThat(notToken).isNull()

        var verificationCode = tc.getSMSVerificationCode(phoneNumber)
        val token = tanker1.setVerificationMethod(
            PhoneNumberVerification(phoneNumber, verificationCode),
            options
        ).get()
        assertThat(token).isNotBlank

        val publicIdentity = Identity.getPublicIdentity(identity)
        val expectedMethod = "phone_number"
        val usedMethod = checkSessionToken(publicIdentity, token!!, expectedMethod, phoneNumber)
        assertThat(usedMethod).isEqualTo(expectedMethod)

        tanker1.stop().get()
    }

    @Test
    fun cannot_register_with_preverified_email() {
        val email = "bob@tanker.io"

        tanker1.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker1.registerIdentity(PreverifiedEmailVerification(email)).get()
        }

        assertThat(e.cause).hasCauseInstanceOf(InvalidArgument::class.java)
    }

    @Test
    fun cannot_register_with_preverified_phone_number() {
        val phoneNumber = "+33639982233"

        tanker1.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker1.registerIdentity(PreverifiedPhoneNumberVerification(phoneNumber)).get()
        }

        assertThat(e.cause).hasCauseInstanceOf(InvalidArgument::class.java)
    }

    @Test
    fun cannot_register_with_preverified_oidc() {
        val oidcConfig = Config.getOIDCConfig()

        val oidcProviderConfig =
            OidcProviderConfig(oidcConfig.clientId, oidcConfig.displayName, oidcConfig.issuer)
        val appOptions = TankerAppUpdateOptions()
            .setOidcProvider(oidcProviderConfig)
        val oidc_provider = tc.admin.appUpdate(tc.id(), appOptions)

        val subject = "subject"
        val providerID = oidc_provider.get("id").asText()

        tanker1.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker1.registerIdentity(PreverifiedOIDCVerification(subject, providerID)).get()
        }

        assertThat(e.cause).hasCauseInstanceOf(InvalidArgument::class.java)
    }

    @Test
    fun cannot_verify_with_preverified_email() {
        val email = "bob@tanker.io"

        tanker1.start(identity).get()
        val verificationCode = tc.getEmailVerificationCode(email)
        tanker1.registerIdentity(EmailVerification(email, verificationCode)).get()

        tanker2.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker2.verifyIdentity(PreverifiedEmailVerification(email)).get()
        }

        assertThat(e.cause).hasCauseInstanceOf(InvalidArgument::class.java)
    }

    @Test
    fun cannot_verify_with_preverified_phone_number() {
        val phoneNumber = "+33639982233"

        tanker1.start(identity).get()
        val verificationCode = tc.getSMSVerificationCode(phoneNumber)
        tanker1.registerIdentity(PhoneNumberVerification(phoneNumber, verificationCode)).get()

        tanker2.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker2.verifyIdentity(PreverifiedPhoneNumberVerification(phoneNumber)).get()
        }

        assertThat(e.cause).hasCauseInstanceOf(InvalidArgument::class.java)
    }

    @Test
    fun cannot_verify_with_preverified_oidc() {
        val oidcConfig = Config.getOIDCConfig()

        val oidcProviderConfig =
            OidcProviderConfig(oidcConfig.clientId, oidcConfig.displayName, oidcConfig.issuer)
        val appOptions = TankerAppUpdateOptions()
            .setOidcProvider(oidcProviderConfig)
        val oidc_provider = tc.admin.appUpdate(tc.id(), appOptions)
        val oidcIdToken = getIDToken(oidcConfig)

        val subject = extractSubject(oidcIdToken)
        val providerID = oidc_provider.get("id").asText()

        tanker1.start(identity).get()
        val nonce = tanker1.createOidcNonce().get()
        tanker1.setOidcTestNonce(nonce).get()
        tanker1.registerIdentity(OIDCIDTokenVerification(oidcIdToken)).get()

        tanker2.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker2.verifyIdentity(PreverifiedOIDCVerification(subject, providerID)).get()
        }

        assertThat(e.cause).hasCauseInstanceOf(InvalidArgument::class.java)
    }

    @Test
    fun can_set_preverified_email_with_setVerificationMethod() {
        val pass = "PassOne"
        val email = "bob@tanker.io"

        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(pass)).get()
        assertThat(tanker1.getVerificationMethods().get()).containsExactly(
            PassphraseVerificationMethod
        )

        tanker1.setVerificationMethod(PreverifiedEmailVerification(email)).get()
        assertThat(tanker1.getVerificationMethods().get()).containsExactlyInAnyOrder(
            PreverifiedEmailVerificationMethod(email), PassphraseVerificationMethod
        )

        tanker2.start(identity).get()
        val verificationCode = tc.getEmailVerificationCode(email)
        tanker2.verifyIdentity(EmailVerification(email, verificationCode)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)

        assertThat(tanker1.getVerificationMethods().get()).containsExactlyInAnyOrder(
            EmailVerificationMethod(email), PassphraseVerificationMethod
        )

        tanker1.stop().get()
        tanker2.stop().get()
    }

    @Test
    fun can_set_preverified_phone_number_with_setVerificationMethod() {
        val pass = "PassOne"
        val phoneNumber = "+33639982233"

        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(pass)).get()
        assertThat(tanker1.getVerificationMethods().get()).containsExactly(
            PassphraseVerificationMethod
        )

        tanker1.setVerificationMethod(PreverifiedPhoneNumberVerification(phoneNumber)).get()
        assertThat(tanker1.getVerificationMethods().get()).containsExactlyInAnyOrder(
            PreverifiedPhoneNumberVerificationMethod(phoneNumber), PassphraseVerificationMethod
        )

        tanker2.start(identity).get()
        val verificationCode = tc.getSMSVerificationCode(phoneNumber)
        tanker2.verifyIdentity(PhoneNumberVerification(phoneNumber, verificationCode)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)

        assertThat(tanker1.getVerificationMethods().get()).containsExactlyInAnyOrder(
            PhoneNumberVerificationMethod(phoneNumber), PassphraseVerificationMethod
        )

        tanker1.stop().get()
        tanker2.stop().get()
    }

    @Test
    fun can_set_preverified_oidc_with_setVerificationMethod() {
        val pass = "PassOne"

        val oidcConfig = Config.getOIDCConfig()

        val oidcProviderConfig =
            OidcProviderConfig(oidcConfig.clientId, oidcConfig.displayName, oidcConfig.issuer)
        val appOptions = TankerAppUpdateOptions()
            .setOidcProvider(oidcProviderConfig)
        val oidc_provider = tc.admin.appUpdate(tc.id(), appOptions)
        val oidcIdToken = getIDToken(oidcConfig)

        val subject = extractSubject(oidcIdToken)
        val providerID = oidc_provider.get("id").asText()

        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(pass)).get()
        assertThat(tanker1.getVerificationMethods().get()).containsExactly(
            PassphraseVerificationMethod
        )

        tanker1.setVerificationMethod(PreverifiedOIDCVerification(subject, providerID)).get()
        assertThat(tanker1.getVerificationMethods().get()).containsExactlyInAnyOrder(
            PassphraseVerificationMethod, OIDCIDTokenVerificationMethod(providerID, oidc_provider.get("display_name").asText())
        )

        tanker2.start(identity).get()
        val nonce = tanker2.createOidcNonce().get()
        tanker2.setOidcTestNonce(nonce).get()
        tanker2.verifyIdentity(OIDCIDTokenVerification(oidcIdToken)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)

        tanker1.stop().get()
        tanker2.stop().get()
    }

    @Test
    fun test_register_e2e_passphrase() {
        val passphrase = "mangerbouger.fr"
        tanker1.start(identity).get()
        tanker1.registerIdentity(E2ePassphraseVerification(passphrase)).get()

        tanker2.start(identity).get()
        tanker2.verifyIdentity(E2ePassphraseVerification(passphrase)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)

        tanker1.stop().get()
        tanker2.stop().get()
    }

    @Test
    fun test_update_e2e_passphrase() {
        val oldPassphrase = "alkalosis"
        val newPassphrase = "acidosis"
        tanker1.start(identity).get()
        tanker1.registerIdentity(E2ePassphraseVerification(oldPassphrase)).get()
        tanker1.setVerificationMethod(E2ePassphraseVerification(newPassphrase)).get()
        tanker1.stop().get()

        tanker2.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker2.verifyIdentity(E2ePassphraseVerification(oldPassphrase)).get()
        }
        assertThat(e.cause).hasCauseInstanceOf(InvalidVerification::class.java)

        tanker2.verifyIdentity(E2ePassphraseVerification(newPassphrase)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)
        tanker2.stop().get()
    }

    @Test
    fun test_switch_to_e2e_passphrase() {
        val oldPassphrase = "alkalosis"
        val newPassphrase = "acidosis"
        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(oldPassphrase)).get()
        tanker1.setVerificationMethod(
            E2ePassphraseVerification(newPassphrase),
            VerificationOptions().allowE2eMethodSwitch(true)
        ).get()
        tanker1.stop().get()

        tanker2.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker2.verifyIdentity(PassphraseVerification(oldPassphrase)).get()
        }
        assertThat(e.cause).hasCauseInstanceOf(PreconditionFailed::class.java)

        tanker2.verifyIdentity(E2ePassphraseVerification(newPassphrase)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)
        tanker2.stop().get()
    }

    fun test_switch_from_e2e_passphrase() {
        val oldPassphrase = "alkalosis"
        val newPassphrase = "acidosis"
        tanker1.start(identity).get()
        tanker1.registerIdentity(E2ePassphraseVerification(oldPassphrase)).get()
        tanker1.setVerificationMethod(
            PassphraseVerification(newPassphrase),
            VerificationOptions().allowE2eMethodSwitch(true)
        ).get()
        tanker1.stop().get()

        tanker2.start(identity).get()
        val e = shouldThrow<TankerFutureException> {
            tanker2.verifyIdentity(E2ePassphraseVerification(oldPassphrase)).get()
        }
        assertThat(e.cause).hasCauseInstanceOf(PreconditionFailed::class.java)

        tanker2.verifyIdentity(PassphraseVerification(newPassphrase)).get()
        assertThat(tanker2.getStatus()).isEqualTo(Status.READY)
        tanker2.stop().get()
    }

    @Test
    fun test_cannot_switch_to_e2e_passphrase_without_allow_e2e_switch_flag() {
        val oldPassphrase = "alkalosis"
        val newPassphrase = "acidosis"
        tanker1.start(identity).get()
        tanker1.registerIdentity(PassphraseVerification(oldPassphrase)).get()
        val e = shouldThrow<TankerFutureException> {
            tanker1.setVerificationMethod(
                E2ePassphraseVerification(newPassphrase),
            ).get()
        }
        assertThat(e.cause).hasCauseInstanceOf(InvalidArgument::class.java)
        tanker1.stop().get()
    }
}
