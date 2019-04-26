package io.tanker.api

import io.kotlintest.*
import io.kotlintest.matchers.haveLength
import io.tanker.bindings.TankerErrorCode
import io.tanker.utils.Base64

class TankerTests : TankerSpec() {

    override fun beforeTest(description: Description) {
        options.setTrustchainId(tc.id())
    }

    init {
        "tanker_create fails if the options passed are wrong" {
            options.setTrustchainId("Invalid base64!")
            shouldThrow<TankerFutureException> { Tanker(options) }
        }

        "Can create a Tanker object" {
            val tanker = Tanker(options)
            tanker.isOpen() shouldBe false
        }

        "Can get a valid version string" {
            val versionString: String = Tanker.getNativeVersionString()
            versionString shouldNot haveLength(0)
        }

        "Can open a Tanker session with a new token" {
            val tanker = Tanker(options)
            val userId = tc.generateIdentity()
            val token = userId
            tanker.signUp(token).get()
            tanker.isOpen() shouldBe true
            tanker.signOut().get()
        }

        "Can get our device ID" {
            val tanker = Tanker(options)
            val userId = tc.generateIdentity()
            val token = userId
            tanker.signUp(token).get()

            val devId = tanker.getDeviceId()
            val devIdRoundtrip = Base64.encodeToString(Base64.decode(devId))

            devId shouldBe devIdRoundtrip

            tanker.signOut().get()
        }

        "Can encrypt and decrypt back" {
            val tanker = Tanker(options)
            val userId = tc.generateIdentity()
            val token = userId
            tanker.signUp(token).get()

            val plaintext = "plain text"
            val decrypted = tanker.decrypt(tanker.encrypt(plaintext.toByteArray()).get()).get()
            String(decrypted) shouldBe plaintext

            tanker.signOut().get()
        }

        "Can encrypt, share, and decrypt between two users" {
            val aliceId = tc.generateIdentity()
            val bobId = tc.generateIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()

            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()

            val plaintext = "plain text"
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray()).get()
            val shareOptions = TankerShareOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            tankerAlice.share(arrayOf(tankerAlice.getResourceID(encrypted)), shareOptions).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can encrypt-and-share, then decrypt, between two users" {
            val aliceId = tc.generateIdentity()
            val bobId = tc.generateIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()

            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()

            val plaintext = "There are no mistakes, just happy accidents"
            val encryptOptions = TankerEncryptOptions().shareWithUsers(Identity.getPublicIdentity(bobId))
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can share with a provisional user" {
            val aliceId = tc.generateIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()

            val bobEmail = "bob@tanker.io"
            val bobProvisionalIdentity = Identity.createProvisionalIdentity(tc.id(), bobEmail)

            val message = "This is for future Bob"
            val bobPublicIdentity = Identity.getPublicIdentity(bobProvisionalIdentity)
            val encryptOptions = TankerEncryptOptions().shareWithUsers(bobPublicIdentity)

            val encrypted = tankerAlice.encrypt(message.toByteArray(), encryptOptions).get()

            val tankerBob = Tanker(options)
            val bobPrivateIdentity = tc.generateIdentity()
            tankerBob.signUp(bobPrivateIdentity).get()

            val bobVerificationCode = tc.admin.getVerificationCode(tc.id(), bobEmail).get()
            tankerBob.claimProvisionalIdentity(bobProvisionalIdentity, bobVerificationCode).get()

            val decrypted = tankerBob.decrypt(encrypted).get()
            String(decrypted) shouldBe "This is for future Bob"

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can self-revoke" {
            val aliceId = tc.generateIdentity()
            var revoked = false

            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()
            tankerAlice.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                revoked = true
            })
            tankerAlice.revokeDevice(tankerAlice.getDeviceId()).get()
            Thread.sleep(500)
            tankerAlice.isOpen() shouldBe false
            revoked shouldBe true

            tankerAlice.signOut().get()
        }

        "Can revoke another device of the same user" {
            val aliceId = tc.generateIdentity()
            var revoked = false

            val tankerAlice1 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice1.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                revoked = true
            })
            tankerAlice1.signUp(aliceId).get()
            val unlockKey = tankerAlice1.generateAndRegisterUnlockKey().get()

            val tankerAlice2 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice2.signIn(aliceId, TankerSignInOptions().setUnlockKey(unlockKey)).get()

            tankerAlice2.revokeDevice(tankerAlice1.getDeviceId()).get()
            Thread.sleep(500)
            tankerAlice1.isOpen() shouldBe false
            revoked shouldBe true

            tankerAlice1.signOut().get()
            tankerAlice2.signOut().get()
        }

        "Cannot revoke a device of another user" {
            val aliceId = tc.generateIdentity()
            val bobId = tc.generateIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()
            tankerAlice.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                assert(false)
            })

            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()

            val aliceDevId = tankerAlice.getDeviceId()
            val e = shouldThrow<TankerFutureException> {
                tankerBob.revokeDevice(aliceDevId).get()
            }
            assert(e.cause is TankerException)
            assert((e.cause as TankerException).errorCode == TankerErrorCode.DEVICE_NOT_FOUND)

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can get a correct device list" {
            val tankerAlice = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice.signUp(tc.generateIdentity()).get()
            tankerAlice.generateAndRegisterUnlockKey().get()

            val devices = tankerAlice.getDeviceList().get()
            devices.size shouldBe 1
            devices[0].getDeviceId() shouldBe tankerAlice.getDeviceId()
            devices[0].isRevoked() shouldBe false
        }

        "Can get a correct device list after revocation" {
            val aliceId = tc.generateIdentity()
            val tankerAlice1 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice1.signUp(aliceId).get()
            val aliceDeviceId1 = tankerAlice1.getDeviceId()

            val unlockKey = tankerAlice1.generateAndRegisterUnlockKey().get()
            val tankerAlice2 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice2.signIn(aliceId, TankerSignInOptions().setUnlockKey(unlockKey)).get()
            val aliceDeviceId2 = tankerAlice2.getDeviceId()

            tankerAlice2.revokeDevice(tankerAlice1.getDeviceId()).get()
            Thread.sleep(500)

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

            tankerAlice2.signOut().get()
        }
    }
}
