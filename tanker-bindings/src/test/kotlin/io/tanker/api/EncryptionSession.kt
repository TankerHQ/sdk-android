package io.tanker.api

import io.kotlintest.*

class EncryptionSessionTests : TankerSpec() {
    lateinit var aliceId: String
    lateinit var bobId: String
    lateinit var tankerAlice: Tanker
    lateinit var tankerBob: Tanker

    override fun beforeTest(testCase: TestCase) {
        aliceId = tc.createIdentity()
        bobId = tc.createIdentity()

        tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("paßwörd")).get()

        tankerBob = Tanker(options)
        tankerBob.start(bobId).get()
        tankerBob.registerIdentity(PassphraseVerification("paßwörd")).get()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    init {
        "Can open and close native encryption sessions" {
            tankerAlice.createEncryptionSession(SharingOptions()).get()
            System.gc() // Try to have HotSpot close the session before the test ends
        }

        "Can encrypt an empty string with an encryption session" {
            val plaintext = ""
            val sess = tankerAlice.createEncryptionSession(null).get()
            val encrypted = sess.encrypt(plaintext.toByteArray()).get()
            String(tankerAlice.decrypt(encrypted).get()) shouldBe plaintext
        }

        "Can share with Bob using an encryption session" {
            val plaintext = "La Pléiade"
            val shareOpt = SharingOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            val sess = tankerAlice.createEncryptionSession(shareOpt).get()
            val encrypted = sess.encrypt(plaintext.toByteArray()).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext
        }

        "Can share with Bob using an encryption session (non-deprecated API)" {
            val plaintext = "La Pléiade"
            val encOpt = EncryptionOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            val sess = tankerAlice.createEncryptionSession(encOpt).get()
            val encrypted = sess.encrypt(plaintext.toByteArray()).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext
        }

        "Can encrypt a stream in an encryption session" {
            val sess = tankerAlice.createEncryptionSession(SharingOptions()).get()
            val plaintext = "La Comédie Humaine".toByteArray()
            val plaintextChannel = TankerChannels.fromInputStream(plaintext.inputStream())
            val encryptStream = sess.encrypt(plaintextChannel).get()

            val decryptionStream = TankerChannels.toInputStream(tankerAlice.decrypt(encryptStream).get())
            val decrypted = ByteArray(plaintext.size) { 0 }
            decryptionStream.read(decrypted) shouldBe plaintext.size
            decrypted shouldBe plaintext
        }

        "Resource IDs of the session and ciphertext match" {
            val sess = tankerAlice.createEncryptionSession(SharingOptions()).get()
            val encrypted = sess.encrypt("Les Rougon-Macquart".toByteArray()).get()
            tankerAlice.getResourceID(encrypted) shouldBe sess.getResourceId()
        }

        "Ciphertexts from different sessions have different resource IDs" {
            val sess1 = tankerAlice.createEncryptionSession(SharingOptions()).get()
            val sess2 = tankerAlice.createEncryptionSession(SharingOptions()).get()
            val cipher1 = sess1.encrypt("La Fontaine — Fables".toByteArray()).get()
            val cipher2 = sess2.encrypt("Monmoulin — Lettres".toByteArray()).get()
            tankerAlice.getResourceID(cipher1) shouldNotBe  tankerAlice.getResourceID(cipher2)
        }

        "Different sessions encrypt with different keys" {
            val shareOpt = SharingOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            val sessShared = tankerAlice.createEncryptionSession(shareOpt).get()
            val sessPrivate = tankerAlice.createEncryptionSession(SharingOptions()).get()

            val plaintext = "Les Crimes Célèbres"
            val shared = sessShared.encrypt(plaintext.toByteArray()).get()
            val private = sessPrivate.encrypt(plaintext.toByteArray()).get()

            tankerBob.decrypt(shared).get()
            val e = shouldThrow<TankerFutureException> { tankerBob.decrypt(private).get() }
            (e.cause is TankerException) shouldBe true
        }
    }
}
