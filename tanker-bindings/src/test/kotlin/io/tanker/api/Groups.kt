package io.tanker.api
import io.kotlintest.TestCaseConfig
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.seconds
import io.kotlintest.specs.StringSpec
import io.tanker.bindings.TankerError
import io.tanker.bindings.TankerErrorCode
import java.util.*

class GroupTests : StringSpec() {
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
        "Cannot create an empty group" {
            val aliceId = UUID.randomUUID().toString()
            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()

            val e = shouldThrow<TankerFutureException> {
                tankerAlice.createGroup(arrayOf()).get()
            }
            (e.cause is TankerException) shouldBe true
            (e.cause as TankerException).errorCode shouldBe TankerErrorCode.INVALID_GROUP_SIZE

            tankerAlice.close().get()
        }

        "Can create a valid group" {
            val aliceId = UUID.randomUUID().toString()
            val bobId = UUID.randomUUID().toString()

            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()

            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            tankerAlice.createGroup(arrayOf(aliceId, bobId)).get()

            tankerAlice.close().get()
            tankerBob.close().get()
        }

        "Can share to group" {
            val aliceId = UUID.randomUUID().toString()
            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()
            val bobId = UUID.randomUUID().toString()
            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            val plaintext = "Two's company, three's a crowd"
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray()).get()
            val groupId = tankerAlice.createGroup(arrayOf(aliceId, bobId)).get()
            tankerAlice.share(arrayOf(tankerAlice.getResourceID(encrypted)), TankerShareOptions().shareWithGroups(groupId)).get()

            String(tankerBob.decrypt(encrypted).get()) shouldEqual plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
        }

        "Can encrypt-and-share to group" {
            val aliceId = UUID.randomUUID().toString()
            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()
            val bobId = UUID.randomUUID().toString()
            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            val plaintext = "Two's company, three's a crowd"
            val groupId = tankerAlice.createGroup(arrayOf(aliceId, bobId)).get()
            val encryptOptions = TankerEncryptOptions().shareWithGroups(groupId)
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()

            String(tankerBob.decrypt(encrypted).get()) shouldEqual plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
        }

        "Can share to an external group" {
            val aliceId = UUID.randomUUID().toString()
            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()
            val bobId = UUID.randomUUID().toString()
            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            val groupId = tankerAlice.createGroup(arrayOf(aliceId)).get()

            val plaintext = "Two's company, three's a crowd"
            val encrypted = tankerBob.encrypt(plaintext.toByteArray()).get()
            tankerBob.share(arrayOf(tankerBob.getResourceID(encrypted)), TankerShareOptions().shareWithGroups(groupId)).get()

            String(tankerAlice.decrypt(encrypted).get()) shouldEqual plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
        }

        "Can add a member to a group" {
            val aliceId = UUID.randomUUID().toString()
            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()
            val bobId = UUID.randomUUID().toString()
            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()

            val plaintext = "Two's company, three's a crowd"
            val groupId = tankerAlice.createGroup(arrayOf(aliceId)).get()
            val encryptOptions = TankerEncryptOptions().shareWithGroups(groupId)
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()

            tankerAlice.updateGroupMembers(groupId, usersToAdd = arrayOf(bobId)).get()

            String(tankerBob.decrypt(encrypted).get()) shouldEqual plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
        }

        "Can transitively add members to a group" {
            val aliceId = UUID.randomUUID().toString()
            val tankerAlice = Tanker(options)
            tankerAlice.open(aliceId, tc.generateUserToken(aliceId)).get()
            val bobId = UUID.randomUUID().toString()
            val tankerBob = Tanker(options)
            tankerBob.open(bobId, tc.generateUserToken(bobId)).get()
            val charlieId = UUID.randomUUID().toString()
            val tankerCharlie = Tanker(options)
            tankerCharlie.open(charlieId, tc.generateUserToken(charlieId)).get()

            val groupId = tankerAlice.createGroup(arrayOf(bobId)).get()
            tankerBob.updateGroupMembers(groupId, usersToAdd = arrayOf(charlieId)).get()
            tankerCharlie.updateGroupMembers(groupId, usersToAdd = arrayOf(aliceId)).get()

            val plaintext = "plain text"
            val encryptOptions = TankerEncryptOptions().shareWithGroups(groupId)
            val encrypted = tankerCharlie.encrypt(plaintext.toByteArray(), encryptOptions).get()
            String(tankerAlice.decrypt(encrypted).get()) shouldEqual plaintext

            tankerAlice.close().get()
            tankerBob.close().get()
            tankerCharlie.close().get()
        }
    }
}
