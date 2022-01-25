package io.tanker.api

import io.tanker.api.Tanker.Companion.prehashPassword
import io.tanker.api.errors.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class TankerTests : TankerSpec() {
    @Before
    fun beforeTest() {
        options.setAppId(tc.id())
    }

    @Test
    fun tanker_create_fails_if_the_options_passed_are_wrong() {
        options.setAppId("Invalid base64!")
        val e = shouldThrow<TankerFutureException> { Tanker(options) }
        assertThat(e).hasCauseInstanceOf(InvalidArgument::class.java)
    }

    @Test
    fun can_create_a_Tanker_object() {
        val tanker = Tanker(options)
        assertThat(tanker.getStatus()).isEqualTo(Status.STOPPED)
    }

    @Test
    fun can_get_a_valid_version_string() {
        val versionString: String = Tanker.getNativeVersionString()
        assertThat(versionString.length).isGreaterThan(0)
    }

    @Test
    fun can_open_a_Tanker_session_by_starting() {
        val tanker = Tanker(options)
        val identity = tc.createIdentity()
        val status = tanker.start(identity).get()
        assertThat(status).isEqualTo(Status.IDENTITY_REGISTRATION_NEEDED)
        assertThat(tanker.getStatus()).isEqualTo(Status.IDENTITY_REGISTRATION_NEEDED)
        tanker.registerIdentity(PassphraseVerification("pass")).get()
        assertThat(tanker.getStatus()).isEqualTo(Status.READY)
        tanker.stop().get()
    }

    @Test
    fun can_close_and_reopen_a_session() {
        val tanker = Tanker(options)
        val identity = tc.createIdentity()
        var status = tanker.start(identity).get()
        assertThat(status).isEqualTo(Status.IDENTITY_REGISTRATION_NEEDED)
        assertThat(tanker.getStatus()).isEqualTo(Status.IDENTITY_REGISTRATION_NEEDED)
        tanker.registerIdentity(PassphraseVerification("pass")).get()
        assertThat(tanker.getStatus()).isEqualTo(Status.READY)
        tanker.stop().get()

        status = tanker.start(identity).get()
        assertThat(status).isEqualTo(Status.READY)
        tanker.stop().get()
    }

    @Test
    fun cant_open_the_same_device_twice() {
        val tanker = Tanker(options)
        val identity = tc.createIdentity()
        val status = tanker.start(identity).get()
        assertThat(status).isEqualTo(Status.IDENTITY_REGISTRATION_NEEDED)
        assertThat(tanker.getStatus()).isEqualTo(Status.IDENTITY_REGISTRATION_NEEDED)
        tanker.registerIdentity(PassphraseVerification("pass")).get()
        assertThat(tanker.getStatus()).isEqualTo(Status.READY)

        val tanker2 = Tanker(options)
        val ex = shouldThrow<TankerFutureException> { tanker2.start(identity).get() }
        assertThat(ex).hasCauseInstanceOf(PreconditionFailed::class.java)

        tanker.stop().get()
    }

    @Test
    fun reports_synchronous_http_errors_correctly() {
        val badOptions = TankerOptions()
        badOptions.appId = options.appId
        badOptions.persistentPath = options.persistentPath
        badOptions.cachePath = options.cachePath
        // This error should be reported before any network call
        badOptions.url = "this is not an url at all"
        val tanker = Tanker(badOptions)
        val identity = tc.createIdentity()
        val ex = shouldThrow<TankerFutureException> { tanker.start(identity).get() }
        assertThat(ex).hasCauseInstanceOf(NetworkError::class.java)
    }

    @Test
    fun reports_asynchronous_http_errors_correctly() {
        val badOptions = TankerOptions()
        badOptions.appId = options.appId
        badOptions.persistentPath = options.persistentPath
        badOptions.cachePath = options.cachePath
        // This error requires an (async) DNS lookup
        badOptions.url = "https://this-is-not-a-tanker-server.com"
        val tanker = Tanker(badOptions)
        val identity = tc.createIdentity()
        val ex = shouldThrow<TankerFutureException> { tanker.start(identity).get() }
        assertThat(ex).hasCauseInstanceOf(NetworkError::class.java)
    }

    @Test
    fun can_encrypt_an_empty_buffer() {
        val tanker = Tanker(options)
        val identity = tc.createIdentity()
        tanker.start(identity).get()
        tanker.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = ""
        val decrypted = tanker.decrypt(tanker.encrypt(plaintext.toByteArray()).get()).get()
        assertThat(String(decrypted)).isEqualTo(plaintext)

        tanker.stop().get()
    }

    @Test
    fun can_encrypt_and_decrypt_back() {
        val tanker = Tanker(options)
        val identity = tc.createIdentity()
        tanker.start(identity).get()
        tanker.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = "plain text"
        val decrypted = tanker.decrypt(tanker.encrypt(plaintext.toByteArray()).get()).get()
        assertThat(String(decrypted)).isEqualTo(plaintext)

        tanker.stop().get()
    }

    @Test
    fun can_stop_tanker_while_a_call_is_in_flight() {
        val tanker = Tanker(options)
        val identity = tc.createIdentity()
        tanker.start(identity).get()
        tanker.registerIdentity(PassphraseVerification("pass")).get()

        // Do not wait for the future, and stop tanker
        tanker.encrypt("plain text".toByteArray())

        tanker.stop().get()
    }

    @Test
    fun can_stream_encrypt_and_stream_decrypt_back() {
        val tanker = Tanker(options)
        val identity = tc.createIdentity()
        tanker.start(identity).get()
        tanker.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = ByteArray(3 * 1024 * 1024)
        val clear = InputStreamWrapper(plaintext.inputStream())

        val encryptor = tanker.encrypt(clear).get()
        val decryptor = tanker.decrypt(encryptor).get()

        val decrypted = TankerInputStream(decryptor).readBytes()
        assertThat(decrypted).isEqualTo(plaintext)
        tanker.stop().get()
    }

    @Test
    fun can_encrypt_share_and_decrypt_between_two_users() {
        val aliceId = tc.createIdentity()
        val bobId = tc.createIdentity()

        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val tankerBob = Tanker(options)
        tankerBob.start(bobId).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = "plain text"
        val encrypted = tankerAlice.encrypt(plaintext.toByteArray()).get()
        val shareOptions = SharingOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
        tankerAlice.share(arrayOf(tankerAlice.getResourceID(encrypted)), shareOptions).get()
        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_encrypt_without_sharing_with_self() {
        val aliceId = tc.createIdentity()
        val bobId = tc.createIdentity()

        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val tankerBob = Tanker(options)
        tankerBob.start(bobId).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = "plain text"
        val encryptionOptions = EncryptionOptions()
                .shareWithUsers(Identity.getPublicIdentity(bobId))
                .shareWithSelf(false)
        val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptionOptions).get()

        val ex = shouldThrow<TankerFutureException> { tankerAlice.decrypt(encrypted).get() }
        assertThat(ex).hasCauseInstanceOf(InvalidArgument::class.java)

        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_retrieve_the_resource_ID_in_both_encryption_and_decryption_streams() {
        val tanker = Tanker(options)
        val identity = tc.createIdentity()
        tanker.start(identity).get()
        tanker.registerIdentity(PassphraseVerification("pass")).get()

        val clear = InputStreamWrapper(ByteArray(0).inputStream())

        val encryptor = tanker.encrypt(clear).get()
        val decryptor = tanker.decrypt(encryptor).get()

        assertThat(tanker.getResourceID(encryptor)).isEqualTo(tanker.getResourceID(decryptor))
        tanker.stop().get()
    }

    @Test
    fun can_stream_encrypt_share_and_stream_decrypt_between_two_users() {
        val aliceId = tc.createIdentity()
        val bobId = tc.createIdentity()

        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val tankerBob = Tanker(options)
        tankerBob.start(bobId).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = "plain text"
        val channel = InputStreamWrapper(plaintext.toByteArray().inputStream())
        val encryptor = tankerAlice.encrypt(channel).get()
        val shareOptions = SharingOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
        tankerAlice.share(arrayOf(tankerAlice.getResourceID(encryptor)), shareOptions).get()
        val decryptionStream = TankerInputStream(tankerBob.decrypt(encryptor).get())
        assertThat(String(decryptionStream.readBytes())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_stream_encrypt_and_share_then_stream_decrypt_between_two_users() {
        val aliceId = tc.createIdentity()
        val bobId = tc.createIdentity()

        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val tankerBob = Tanker(options)
        tankerBob.start(bobId).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = "There are no mistakes, just happy accidents"
        val encryptOptions = EncryptionOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
        val channel = InputStreamWrapper(plaintext.toByteArray().inputStream())
        val encryptor = tankerAlice.encrypt(channel, encryptOptions).get()
        val decryptionStream = TankerInputStream(tankerBob.decrypt(encryptor).get())
        assertThat(String(decryptionStream.readBytes())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_encrypt_and_share_then_decrypt_between_two_users() {
        val aliceId = tc.createIdentity()
        val bobId = tc.createIdentity()

        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val tankerBob = Tanker(options)
        tankerBob.start(bobId).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = "There are no mistakes, just happy accidents"
        val encryptOptions = EncryptionOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
        val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()
        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_share_with_a_provisional_user() {
        val aliceId = tc.createIdentity()
        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val bobEmail = "bob@tanker.io"
        val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

        val message = "This is for future Bob"
        val bobPublicIdentity = Identity.getPublicIdentity(bobProvisionalIdentity)
        val encryptOptions = EncryptionOptions().shareWithUsers(bobPublicIdentity)

        val encrypted = tankerAlice.encrypt(message.toByteArray(), encryptOptions).get()

        val tankerBob = Tanker(options)
        val bobPrivateIdentity = tc.createIdentity()
        tankerBob.start(bobPrivateIdentity).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        val attachResult = tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
        assertThat(attachResult.status).isEqualTo(Status.IDENTITY_VERIFICATION_NEEDED)
        val bobVerificationCode = tc.getEmailVerificationCode(bobEmail)
        tankerBob.verifyProvisionalIdentity(EmailVerification(bobEmail, bobVerificationCode)).get()

        val decrypted = tankerBob.decrypt(encrypted).get()
        assertThat(String(decrypted)).isEqualTo("This is for future Bob")

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_skip_provisional_identity_verification() {
        val aliceId = tc.createIdentity()
        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val bobEmail = "bob@tanker.io"
        val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

        val message = "This is for future Bob"
        val bobPublicIdentity = Identity.getPublicIdentity(bobProvisionalIdentity)
        val encryptOptions = EncryptionOptions().shareWithUsers(bobPublicIdentity)

        val encrypted = tankerAlice.encrypt(message.toByteArray(), encryptOptions).get()

        val tankerBob = Tanker(options)
        val bobPrivateIdentity = tc.createIdentity()
        tankerBob.start(bobPrivateIdentity).get()
        val bobVerificationCode = tc.getEmailVerificationCode(bobEmail)
        tankerBob.registerIdentity(EmailVerification(bobEmail, bobVerificationCode)).get()

        val attachResult = tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
        assertThat(attachResult.status).isEqualTo(Status.READY)
        assertThat(attachResult.verificationMethod).isEqualTo(null)

        val decrypted = tankerBob.decrypt(encrypted).get()
        assertThat(String(decrypted)).isEqualTo("This is for future Bob")

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_attach_even_if_there_is_no_share() {
        val bobEmail = "bob@tanker.io"
        val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

        val tankerBob = Tanker(options)
        val bobPrivateIdentity = tc.createIdentity()
        tankerBob.start(bobPrivateIdentity).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
        val bobVerificationCode = tc.getEmailVerificationCode(bobEmail)
        tankerBob.verifyProvisionalIdentity(EmailVerification(bobEmail, bobVerificationCode)).get()

        tankerBob.stop().get()
    }

    @Test
    fun can_attach_a_provisional_identity_twice() {
        val aliceId = tc.createIdentity()
        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val bobEmail = "bob@tanker.io"
        val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

        val message = "This is for future Bob"
        val bobPublicIdentity = Identity.getPublicIdentity(bobProvisionalIdentity)
        val encryptOptions = EncryptionOptions().shareWithUsers(bobPublicIdentity)

        tankerAlice.encrypt(message.toByteArray(), encryptOptions).get()

        val tankerBob = Tanker(options)
        val bobPrivateIdentity = tc.createIdentity()
        tankerBob.start(bobPrivateIdentity).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        val attachResult = tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
        assertThat(attachResult.status).isEqualTo(Status.IDENTITY_VERIFICATION_NEEDED)
        val bobVerificationCode = tc.getEmailVerificationCode(bobEmail)
        tankerBob.verifyProvisionalIdentity(EmailVerification(bobEmail, bobVerificationCode)).get()

        val attachResult2 = tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
        assertThat(attachResult2.status).isEqualTo(Status.READY)

        tankerBob.stop().get()
        tankerAlice.stop().get()
    }

    @Test
    fun cannot_attach_an_already_attached_identity() {
        val aliceId = tc.createIdentity()
        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val aliceEmail = "alice@tanker.io"
        val provisionalIdentity = Identity.createProvisionalIdentity(tc.id(), aliceEmail)

        val attachAliceResult = tankerAlice.attachProvisionalIdentity(provisionalIdentity).get()
        assertThat(attachAliceResult.status).isEqualTo(Status.IDENTITY_VERIFICATION_NEEDED)
        val aliceVerificationCode = tc.getEmailVerificationCode(aliceEmail)
        tankerAlice.verifyProvisionalIdentity(EmailVerification(aliceEmail, aliceVerificationCode)).get()

        // try to attach/verify with Bob
        val bobId = tc.createIdentity()
        val tankerBob = Tanker(options)
        tankerBob.start(bobId).get()
        tankerBob.registerIdentity(PassphraseVerification("pass")).get()

        val attachBobResult = tankerBob.attachProvisionalIdentity(provisionalIdentity).get()
        assertThat(attachBobResult.status).isEqualTo(Status.IDENTITY_VERIFICATION_NEEDED)
        val bobVerificationCode = tc.getEmailVerificationCode(aliceEmail)
        val e = shouldThrow<TankerFutureException> {
            tankerBob.verifyProvisionalIdentity(EmailVerification(aliceEmail, bobVerificationCode)).get()
        }
        assertThat(e).hasCauseInstanceOf(IdentityAlreadyAttached::class.java) 
    }

    @Test
    fun can_get_a_correct_device_list() {
        val tankerAlice = Tanker(options.setPersistentPath(createTmpDir().toString()).setCachePath(createTmpDir().toString()))
        tankerAlice.start(tc.createIdentity()).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val devices = tankerAlice.getDeviceList().get()
        assertThat(devices.size).isEqualTo(1)
        assertThat(devices[0].getDeviceId()).isEqualTo(tankerAlice.getDeviceId())
        assertThat(devices[0].isRevoked()).isEqualTo(false)
        tankerAlice.stop().get()
    }

    @Test
    fun prehashPassword_empty_string() {
        val e = shouldThrow<TankerFutureException> { prehashPassword("") }
        assertThat(e).hasCauseInstanceOf(InvalidArgument::class.java)
    }

    @Test
    fun prehashPassword_test_vector() {
        val input = "super secretive password"
        val expected = "UYNRgDLSClFWKsJ7dl9uPJjhpIoEzadksv/Mf44gSHI="

        assertThat(prehashPassword(input)).isEqualTo(expected)
    }

    @Test
    fun prehashPassword_test_vector_2() {
        val input = "test éå 한국어 😃"
        val expected = "Pkn/pjub2uwkBDpt2HUieWOXP5xLn0Zlen16ID4C7jI="

        assertThat(prehashPassword(input)).isEqualTo(expected)
    }
}
