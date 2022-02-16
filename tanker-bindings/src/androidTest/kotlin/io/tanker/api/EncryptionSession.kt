package io.tanker.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class EncryptionSessionTests : TankerSpec() {
    lateinit var aliceId: String
    lateinit var bobId: String
    lateinit var tankerAlice: Tanker
    lateinit var tankerBob: Tanker

    @Before
    fun beforeTest() {
        aliceId = tc.createIdentity()
        bobId = tc.createIdentity()

        tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("paßwörd")).get()

        tankerBob = Tanker(options)
        tankerBob.start(bobId).get()
        tankerBob.registerIdentity(PassphraseVerification("paßwörd")).get()
    }

    @After
    fun afterTest() {
        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_open_and_close_native_encryption_sessions() {
        tankerAlice.createEncryptionSession(EncryptionOptions()).get()
        System.gc() // Try to have HotSpot close the session before the test ends
    }

    @Test
    fun can_encrypt_an_empty_string_with_an_encryption_session() {
        val plaintext = ""
        val sess = tankerAlice.createEncryptionSession(null).get()
        val encrypted = sess.encrypt(plaintext.toByteArray()).get()
        assertThat(String(tankerAlice.decrypt(encrypted).get())).isEqualTo(plaintext)
    }

    @Test
    fun can_share_with_Bob_using_an_encryption_session() {
        val plaintext = "La Pléiade"
        val encryptOpt = EncryptionOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
        val sess = tankerAlice.createEncryptionSession(encryptOpt).get()
        val encrypted = sess.encrypt(plaintext.toByteArray()).get()
        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)
    }

    @Test
    fun can_share_with_group_using_an_encryption_session() {
        val plaintext = "La Pléiade"
        val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(bobId)).get()
        val encryptOpt = EncryptionOptions().shareWithGroups(groupId)
        val sess = tankerAlice.createEncryptionSession(encryptOpt).get()
        val encrypted = sess.encrypt(plaintext.toByteArray()).get()
        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)
    }

    @Test
    fun can_share_with_Bob_using_an_encryption_session_non_deprecated_API() {
        val plaintext = "La Pléiade"
        val encOpt = EncryptionOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
        val sess = tankerAlice.createEncryptionSession(encOpt).get()
        val encrypted = sess.encrypt(plaintext.toByteArray()).get()
        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)
    }

    @Test
    fun can_encrypt_a_stream_in_an_encryption_session() {
        val sess = tankerAlice.createEncryptionSession(EncryptionOptions()).get()
        val plaintext = "La Comédie Humaine".toByteArray()
        val plaintextChannel = TankerChannels.fromInputStream(plaintext.inputStream())
        val encryptStream = sess.encrypt(plaintextChannel).get()

        val decryptionStream = TankerChannels.toInputStream(tankerAlice.decrypt(encryptStream).get())
        val decrypted = ByteArray(plaintext.size) { 0 }
        assertThat(decryptionStream.read(decrypted)).isEqualTo(plaintext.size)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun resource_IDs_of_the_session_and_ciphertext_match() {
        val sess = tankerAlice.createEncryptionSession(EncryptionOptions()).get()
        val encrypted = sess.encrypt("Les Rougon-Macquart".toByteArray()).get()
        assertThat(tankerAlice.getResourceID(encrypted)).isEqualTo(sess.getResourceId())
    }

    @Test
    fun ciphertexts_from_different_sessions_have_different_resource_IDs() {
        val sess1 = tankerAlice.createEncryptionSession(EncryptionOptions()).get()
        val sess2 = tankerAlice.createEncryptionSession(EncryptionOptions()).get()
        val cipher1 = sess1.encrypt("La Fontaine — Fables".toByteArray()).get()
        val cipher2 = sess2.encrypt("Monmoulin — Lettres".toByteArray()).get()
        assertThat(tankerAlice.getResourceID(cipher1)).isNotEqualTo(tankerAlice.getResourceID(cipher2))
    }

    @Test
    fun different_sessions_encrypt_with_different_keys() {
        val encryptOpt = EncryptionOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
        val sessShared = tankerAlice.createEncryptionSession(encryptOpt).get()
        val sessPrivate = tankerAlice.createEncryptionSession(EncryptionOptions()).get()

        val plaintext = "Les Crimes Célèbres"
        val shared = sessShared.encrypt(plaintext.toByteArray()).get()
        val private = sessPrivate.encrypt(plaintext.toByteArray()).get()

        tankerBob.decrypt(shared).get()
        val e = shouldThrow<TankerFutureException> { tankerBob.decrypt(private).get() }
        assertThat((e.cause is TankerException)).isEqualTo(true)
    }
}
