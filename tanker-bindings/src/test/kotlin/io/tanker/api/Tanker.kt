package io.tanker.api

import io.kotlintest.Description
import io.kotlintest.matchers.haveLength
import io.kotlintest.shouldBe
import io.kotlintest.shouldNot
import io.kotlintest.shouldThrow
import io.tanker.bindings.TankerErrorCode
import io.tanker.utils.Base64
import java.util.*

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
            tanker.getStatus() shouldBe TankerStatus.IDLE
        }

        "Can get a valid version string" {
            val versionString: String = Tanker.getNativeVersionString()
            versionString shouldNot haveLength(0)
        }

        "Can open a Tanker session with a new token" {
            val tanker = Tanker(options)
            val userId = UUID.randomUUID().toString()
            val token = tc.generateUserToken(userId)
            tanker.open(userId, token).get()
            tanker.getStatus() shouldBe TankerStatus.OPEN
            tanker.close().get()
        }

        "Can get our device ID" {
            val tanker = Tanker(options)
            val userId = UUID.randomUUID().toString()
            val token = tc.generateUserToken(userId)
            tanker.open(userId, token).get()

            val devId = tanker.getDeviceId().get()
            val devIdRoundtrip = Base64.encodeToString(Base64.decode(devId))

            devId shouldBe devIdRoundtrip

            tanker.close().get()
        }

        "Can encrypt and decrypt back" {
            val tanker = Tanker(options)
            val userId = UUID.randomUUID().toString()
            val token = tc.generateUserToken(userId)
            tanker.open(userId, token).get()

            val plaintext = "plain text"
            val decrypted = tanker.decrypt(tanker.encrypt(plaintext.toByteArray()).get()).get()
            String(decrypted) shouldBe plaintext

            tanker.close().get()
        }

        "Can encrypt, share, and decrypt between two users" {
            val aliceId = UUID.randomUUID().toString()
            val bobId = UUID.randomUUID().toString()

            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()

            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            val plaintext = "plain text"
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray()).get()
            val shareOptions = TankerShareOptions().shareWithUsers(bobId)
            tankerAlice.share(arrayOf(tankerAlice.getResourceID(encrypted)), shareOptions).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
        }

        "Can encrypt-and-share, then decrypt, between two users" {
            val aliceId = UUID.randomUUID().toString()
            val bobId = UUID.randomUUID().toString()

            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()

            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            val plaintext = "There are no mistakes, just happy accidents"
            val encryptOptions = TankerEncryptOptions().shareWithUsers(bobId)
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()
            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
        }

        "Can self-revoke" {
            val aliceId = UUID.randomUUID().toString()
            var revoked = false

            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()
            tankerAlice.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                revoked = true
            })
            tankerAlice.revokeDevice(tankerAlice.getDeviceId().get()).get()
            Thread.sleep(500)
            tankerAlice.getStatus() shouldBe TankerStatus.IDLE
            revoked shouldBe true

            tankerAlice.close().get()
        }

        "Can revoke another device of the same user" {
            val aliceId = UUID.randomUUID().toString()
            var revoked = false

            val tankerAlice1 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice1.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                revoked = true
            })
            tankerAlice1.open(aliceId, tc.generateUserToken(aliceId)).get()
            val unlockKey = tankerAlice1.generateAndRegisterUnlockKey().get()

            val tankerAlice2 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tankerAlice2.connectUnlockRequiredHandler(TankerUnlockRequiredHandler{
                tankerAlice2.unlockCurrentDeviceWithUnlockKey(unlockKey).get()
            })
            tankerAlice2.open(aliceId, tc.generateUserToken(aliceId)).get()

            tankerAlice2.revokeDevice(tankerAlice1.getDeviceId().get()).get()
            Thread.sleep(500)
            tankerAlice1.getStatus() shouldBe TankerStatus.IDLE
            revoked shouldBe true

            tankerAlice1.close().get()
            tankerAlice2.close().get()
        }

        "Cannot revoke a device of another user" {
            val aliceId = UUID.randomUUID().toString()
            val bobId = UUID.randomUUID().toString()

            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()
            tankerAlice.connectDeviceRevokedHandler(TankerDeviceRevokedHandler {
                assert(false)
            })

            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            val aliceDevId = tankerAlice.getDeviceId().get()
            val e = shouldThrow<TankerFutureException> {
                tankerBob.revokeDevice(aliceDevId).get()
            }
            assert(e.cause is TankerException)
            assert((e.cause as TankerException).errorCode == TankerErrorCode.DEVICE_NOT_FOUND)

            tankerAlice.close().get()
            tankerBob.close().get()
        }
    }
}
