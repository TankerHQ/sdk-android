package io.tanker.api

import io.kotlintest.*
import io.kotlintest.matchers.haveLength
import io.tanker.api.Tanker.Companion.hashPassphrase
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class TankerTests : TankerSpec() {

    override fun beforeTest(testCase: TestCase) {
        options.setTrustchainId(tc.id())
    }

    init {
        "tanker_create fails if the options passed are wrong" {
            options.setTrustchainId("Invalid base64!")
            shouldThrow<TankerFutureException> { Tanker(options) }
        }

        "Can create a Tanker object" {
            val tanker = Tanker(options)
            tanker.getStatus() shouldBe Status.STOPPED
        }

        "Can get a valid version string" {
            val versionString: String = Tanker.getNativeVersionString()
            versionString shouldNot haveLength(0)
        }

        "Can open a Tanker session by starting" {
            val tanker = Tanker(options)
            val identity = tc.createIdentity()
            val status = tanker.start(identity).get()
            status shouldBe Status.IDENTITY_REGISTRATION_NEEDED
            tanker.getStatus() shouldBe Status.IDENTITY_REGISTRATION_NEEDED
            tanker.registerIdentity(PassphraseVerification("pass")).get()
            tanker.getStatus() shouldBe Status.READY
            tanker.stop().get()
        }

        "Can encrypt and decrypt back" {
            val tanker = Tanker(options)
            val identity = tc.createIdentity()
            tanker.start(identity).get()
            tanker.registerIdentity(PassphraseVerification("pass")).get()

            val plaintext = "plain text"
            val decrypted = tanker.decrypt(tanker.encrypt(plaintext.toByteArray()).get()).get()
            String(decrypted) shouldBe plaintext

            tanker.stop().get()
        }

        "Can stream encrypt and stream decrypt back" {
            val tanker = Tanker(options)
            val identity = tc.createIdentity()
            tanker.start(identity).get()
            tanker.registerIdentity(PassphraseVerification("pass")).get()

            val plaintext = ByteArray(3 * 1024 * 1024)
            val clear = InputStreamWrapper(plaintext.inputStream())

            val encryptor = tanker.encrypt(clear).get()
            val decryptor = tanker.decrypt(encryptor).get()

            val decrypted = TankerInputStream(decryptor).readBytes()
            decrypted shouldBe plaintext
            tanker.stop().get()
        }

        "Can encrypt, share, and decrypt between two users" {
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
            val shareOptions = ShareOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            tankerAlice.share(arrayOf(tankerAlice.getResourceID(encrypted)), shareOptions).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can retrieve the resource ID in both encryption and decryption streams" {
            val tanker = Tanker(options)
            val identity = tc.createIdentity()
            tanker.start(identity).get()
            tanker.registerIdentity(PassphraseVerification("pass")).get()

            val clear = InputStreamWrapper(ByteArray(0).inputStream())

            val encryptor = tanker.encrypt(clear).get()
            val decryptor = tanker.decrypt(encryptor).get()

            tanker.getResourceID(encryptor) shouldBe tanker.getResourceID(decryptor)
            tanker.stop().get()
        }

        "Can stream encrypt, share, and stream decrypt between two users" {
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
            val shareOptions = ShareOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            tankerAlice.share(arrayOf(tankerAlice.getResourceID(encryptor)), shareOptions).get()
            val decryptionStream = TankerInputStream(tankerBob.decrypt(encryptor).get())
            String(decryptionStream.readBytes()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can stream encrypt and share, then stream decrypt, between two users" {
            val aliceId = tc.createIdentity()
            val bobId = tc.createIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val plaintext = "There are no mistakes, just happy accidents"
            val encryptOptions = EncryptOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            val channel = InputStreamWrapper(plaintext.toByteArray().inputStream())
            val encryptor = tankerAlice.encrypt(channel, encryptOptions).get()
            val decryptionStream = TankerInputStream(tankerBob.decrypt(encryptor).get())
            String(decryptionStream.readBytes()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can encrypt-and-share, then decrypt, between two users" {
            val aliceId = tc.createIdentity()
            val bobId = tc.createIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val plaintext = "There are no mistakes, just happy accidents"
            val encryptOptions = EncryptOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can share with a provisional user" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

            val bobEmail = "bob@tanker.io"
            val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

            val message = "This is for future Bob"
            val bobPublicIdentity = Identity.getPublicIdentity(bobProvisionalIdentity)
            val encryptOptions = EncryptOptions().shareWithUsers(bobPublicIdentity)

            val encrypted = tankerAlice.encrypt(message.toByteArray(), encryptOptions).get()

            val tankerBob = Tanker(options)
            val bobPrivateIdentity = tc.createIdentity()
            tankerBob.start(bobPrivateIdentity).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val attachResult = tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
            attachResult.status shouldBe Status.IDENTITY_VERIFICATION_NEEDED
            val bobVerificationCode = tc.admin.getVerificationCode(tc.id(), bobEmail).get()
            tankerBob.verifyProvisionalIdentity(EmailVerification(bobEmail, bobVerificationCode)).get()

            val decrypted = tankerBob.decrypt(encrypted).get()
            String(decrypted) shouldBe "This is for future Bob"

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can skip provisional identity verification" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

            val bobEmail = "bob@tanker.io"
            val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

            val message = "This is for future Bob"
            val bobPublicIdentity = Identity.getPublicIdentity(bobProvisionalIdentity)
            val encryptOptions = EncryptOptions().shareWithUsers(bobPublicIdentity)

            val encrypted = tankerAlice.encrypt(message.toByteArray(), encryptOptions).get()

            val tankerBob = Tanker(options)
            val bobPrivateIdentity = tc.createIdentity()
            tankerBob.start(bobPrivateIdentity).get()
            val bobVerificationCode = tc.admin.getVerificationCode(tc.id(), bobEmail).get()
            tankerBob.registerIdentity(EmailVerification(bobEmail, bobVerificationCode)).get()

            val attachResult = tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
            attachResult.status shouldBe Status.READY
            attachResult.verificationMethod shouldBe null

            val decrypted = tankerBob.decrypt(encrypted).get()
            String(decrypted) shouldBe "This is for future Bob"

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can attach even if there is no share" {
            val bobEmail = "bob@tanker.io"
            val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

            val tankerBob = Tanker(options)
            val bobPrivateIdentity = tc.createIdentity()
            tankerBob.start(bobPrivateIdentity).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
            val bobVerificationCode = tc.admin.getVerificationCode(tc.id(), bobEmail).get()
            tankerBob.verifyProvisionalIdentity(EmailVerification(bobEmail, bobVerificationCode)).get()

            tankerBob.stop().get()
        }

        "Can attach a provisional identity twice" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

            val bobEmail = "bob@tanker.io"
            val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

            val message = "This is for future Bob"
            val bobPublicIdentity = Identity.getPublicIdentity(bobProvisionalIdentity)
            val encryptOptions = EncryptOptions().shareWithUsers(bobPublicIdentity)

            tankerAlice.encrypt(message.toByteArray(), encryptOptions).get()

            val tankerBob = Tanker(options)
            val bobPrivateIdentity = tc.createIdentity()
            tankerBob.start(bobPrivateIdentity).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val attachResult = tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
            attachResult.status shouldBe Status.IDENTITY_VERIFICATION_NEEDED
            val bobVerificationCode = tc.admin.getVerificationCode(tc.id(), bobEmail).get()
            tankerBob.verifyProvisionalIdentity(EmailVerification(bobEmail, bobVerificationCode)).get()

            val attachResult2 = tankerBob.attachProvisionalIdentity(bobProvisionalIdentity).get()
            attachResult2.status shouldBe Status.READY
        }

        "Can self-revoke" {
            val aliceId = tc.createIdentity()
            val revokedSemaphore = Semaphore(0)

            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()
            tankerAlice.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                revokedSemaphore.release()
            })
            tankerAlice.revokeDevice(tankerAlice.getDeviceId()).get()
            val e = shouldThrow<TankerFutureException> { tankerAlice.encrypt("Oh no".toByteArray()).get() }
            assert((e.cause as TankerException).errorCode == ErrorCode.DEVICE_REVOKED)

            tankerAlice.getStatus() shouldBe Status.STOPPED
            val ok = revokedSemaphore.tryAcquire(1, TimeUnit.SECONDS)
            ok shouldBe true
        }

        "Can revoke another device of the same user" {
            val aliceId = tc.createIdentity()
            val revokedSemaphore = Semaphore(0)

            val tankerAlice1 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice1.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                revokedSemaphore.release()
            })
            tankerAlice1.start(aliceId).get()
            tankerAlice1.registerIdentity(PassphraseVerification("pass")).get()

            val tankerAlice2 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice2.start(aliceId).get()
            tankerAlice2.verifyIdentity(PassphraseVerification("pass")).get()

            tankerAlice2.revokeDevice(tankerAlice1.getDeviceId()).get()
            val e = shouldThrow<TankerFutureException> { tankerAlice1.encrypt("Oh no".toByteArray()).get() }
            assert((e.cause as TankerException).errorCode == ErrorCode.DEVICE_REVOKED)
            val ok = revokedSemaphore.tryAcquire(1, TimeUnit.SECONDS)
            ok shouldBe true
            tankerAlice1.getStatus() shouldBe Status.STOPPED

            tankerAlice1.stop().get()
            tankerAlice2.stop().get()
        }

        "Cannot revoke a device of another user" {
            val aliceId = tc.createIdentity()
            val bobId = tc.createIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()
            tankerAlice.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                assert(false)
            })

            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val aliceDevId = tankerAlice.getDeviceId()
            val e = shouldThrow<TankerFutureException> {
                tankerBob.revokeDevice(aliceDevId).get()
            }
            assert(e.cause is TankerException)
            assert((e.cause as TankerException).errorCode == ErrorCode.INVALID_ARGUMENT)

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can get a correct device list" {
            val tankerAlice = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice.start(tc.createIdentity()).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

            val devices = tankerAlice.getDeviceList().get()
            devices.size shouldBe 1
            devices[0].getDeviceId() shouldBe tankerAlice.getDeviceId()
            devices[0].isRevoked() shouldBe false
        }

        "Can get a correct device list after revocation" {
            val aliceId = tc.createIdentity()
            val tankerAlice1 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice1.start(aliceId).get()
            val verificationKey = tankerAlice1.generateVerificationKey().get()
            tankerAlice1.registerIdentity(VerificationKeyVerification(verificationKey)).get()
            val aliceDeviceId1 = tankerAlice1.getDeviceId()

            val tankerAlice2 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice2.start(aliceId).get()
            tankerAlice2.verifyIdentity(VerificationKeyVerification(verificationKey)).get()
            val aliceDeviceId2 = tankerAlice2.getDeviceId()

            tankerAlice2.revokeDevice(tankerAlice1.getDeviceId()).get()
            val devices = tankerAlice2.getDeviceList().get()
            devices.size shouldBe 2
            var foundDevice1 = false
            var foundDevice2 = false

            for (device in devices) {
                when {
                    device.getDeviceId() == aliceDeviceId1 -> {
                        device.isRevoked() shouldBe true
                        foundDevice1 = true
                    }
                    device.getDeviceId() == aliceDeviceId2 -> {
                        device.isRevoked() shouldBe false
                        foundDevice2 = true
                    }
                }
            }

            foundDevice1 shouldBe true
            foundDevice2 shouldBe true

            tankerAlice2.stop().get()
        }

        "hashPassphrase empty string" {
            shouldThrow<TankerFutureException> { hashPassphrase("") }
        }

        "hashPassphrase test vector" {
            val input = "super secretive password"
            val expected = "UYNRgDLSClFWKsJ7dl9uPJjhpIoEzadksv/Mf44gSHI="

            hashPassphrase(input) shouldBe expected
        }

        "hashPassphrase test vector 2" {
            val input = "test éå 한국어 😃"
            val expected = "Pkn/pjub2uwkBDpt2HUieWOXP5xLn0Zlen16ID4C7jI="

            hashPassphrase(input) shouldBe expected
        }
    }
}
