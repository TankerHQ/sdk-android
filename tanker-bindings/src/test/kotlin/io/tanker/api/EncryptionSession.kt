package io.tanker.api

import io.kotlintest.*

class EncryptionSessionTests : TankerSpec() {

    init {
        "Cannot open and close native encryption sessions" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("paßwörd")).get()

            tankerAlice.createEncryptionSession(ShareOptions()).get()
            System.gc() // Try to have HotSpot close the session before the test ends

            tankerAlice.stop().get()
        }

        "Can share to Bob with an encryption session" {
            val aliceId = tc.createIdentity()
            val bobId = tc.createIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("paßwörd")).get()

            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("paßwörd")).get()

            val plaintext = "La Pléiade"
            val shareOpt = ShareOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            val sess = tankerAlice.createEncryptionSession(shareOpt).get()
            val encrypted = sess.encrypt(plaintext.toByteArray()).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can encrypt a stream in an encryption session" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("paßwörd")).get()

            val sess = tankerAlice.createEncryptionSession(ShareOptions()).get()
            val plaintext = "La Comédie Humaine".toByteArray()
            val plaintextChannel = TankerChannels.fromInputStream(plaintext.inputStream())
            val encryptStream = sess.encrypt(plaintextChannel).get()

            val decryptionStream = TankerChannels.toInputStream(tankerAlice.decrypt(encryptStream).get())
            val decrypted = ByteArray(plaintext.size) { 0 }
            decryptionStream.read(decrypted) shouldBe plaintext.size
            decrypted shouldBe plaintext

            tankerAlice.stop().get()
        }

        "Resource IDs of the session and ciphertext match" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("paßwörd")).get()

            val sess = tankerAlice.createEncryptionSession(ShareOptions()).get()
            val encrypted = sess.encrypt("Les Rougon-Macquart".toByteArray()).get()
            tankerAlice.getResourceID(encrypted) shouldBe sess.getResourceId()

            tankerAlice.stop().get()
        }

        "Ciphertexts from different sessions have different resource IDs" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("paßwörd")).get()

            val sess1 = tankerAlice.createEncryptionSession(ShareOptions()).get()
            val sess2 = tankerAlice.createEncryptionSession(ShareOptions()).get()
            val cipher1 = sess1.encrypt("La Fontaine — Fables".toByteArray()).get()
            val cipher2 = sess2.encrypt("Monmoulin — Lettres".toByteArray()).get()
            tankerAlice.getResourceID(cipher1) shouldNotBe  tankerAlice.getResourceID(cipher2)

            tankerAlice.stop().get()
        }

        "Different sessions encrypt with different keys" {
            val aliceId = tc.createIdentity()
            val bobId = tc.createIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("paßwörd")).get()

            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("paßwörd")).get()

            val shareOpt = ShareOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            val sessShared = tankerAlice.createEncryptionSession(shareOpt).get()
            val sessPrivate = tankerAlice.createEncryptionSession(ShareOptions()).get()

            val plaintext = "Les Crimes Célèbres"
            val shared = sessShared.encrypt(plaintext.toByteArray()).get()
            val private = sessPrivate.encrypt(plaintext.toByteArray()).get()

            tankerBob.decrypt(shared).get()
            val e = shouldThrow<TankerFutureException> { tankerBob.decrypt(private).get() }
            (e.cause is TankerException) shouldBe true

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }
    }
}
