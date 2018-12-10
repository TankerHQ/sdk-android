package io.tanker.api
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.StringSpec
import io.kotlintest.TestCaseConfig
import io.kotlintest.matchers.haveLength
import io.kotlintest.matchers.shouldNot
import io.kotlintest.seconds
import java.util.*
import io.tanker.utils.Base64

class TankerTests : StringSpec() {
    private val options = TankerOptions()
    override val defaultTestCaseConfig = TestCaseConfig(timeout = 30.seconds)
    private val tc = TestTrustchain.get()

    init {
        options.setTrustchainId(tc.id())
                .setTrustchainUrl(tc.url)
                .setWritablePath(createTmpDir().toString())
        setupTestEnv()
    }

    init {
        "tanker_create fails if the options passed are wrong" {
            options.setTrustchainId("Invalid base64!")
            shouldThrow<TankerFutureException> { Tanker(options) }
        }

        "Can create a Tanker object" {
            val tanker = Tanker(options)
            tanker.getStatus() shouldEqual TankerStatus.IDLE
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
            tanker.getStatus() shouldEqual TankerStatus.OPEN
            tanker.close().get()
        }

        "Can get our device ID" {
            val tanker = Tanker(options)
            val userId = UUID.randomUUID().toString()
            val token = tc.generateUserToken(userId)
            tanker.open(userId, token).get()

            val devId = tanker.getDeviceId().get()
            val devIdRoundtrip = Base64.encodeToString(Base64.decode(devId))

            devId shouldEqual devIdRoundtrip

            tanker.close().get()
        }

        "Can encrypt and decrypt back" {
            val tanker = Tanker(options)
            val userId = UUID.randomUUID().toString()
            val token = tc.generateUserToken(userId)
            tanker.open(userId, token).get()

            val plaintext = "plain text"
            val decrypted = tanker.decrypt(tanker.encrypt(plaintext.toByteArray()).get()).get()
            String(decrypted) shouldEqual plaintext

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
            String(tankerBob.decrypt(encrypted).get()) shouldEqual plaintext

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
            String(tankerBob.decrypt(encrypted).get()) shouldEqual plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
        }

        "Can encrypt-and-share with the deprecated TankerEncryptOptions APIs" {
            val aliceId = UUID.randomUUID().toString()
            val bobId = UUID.randomUUID().toString()

            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()

            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            val plaintext = "There are no mistakes, just happy accidents"
            @Suppress("DEPRECATION")
            val encryptOptions = TankerEncryptOptions().setRecipients(bobId)
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()
            String(tankerBob.decrypt(encrypted).get()) shouldEqual plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
        }
    }
}
