package io.tanker.api

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.tanker.bindings.TankerErrorCode

class GroupTests : TankerSpec() {

    init {
        "Cannot create an empty group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()

            val e = shouldThrow<TankerFutureException> {
                tankerAlice.createGroup().get()
            }
            (e.cause is TankerException) shouldBe true
            (e.cause as TankerException).errorCode shouldBe TankerErrorCode.INVALID_GROUP_SIZE

            tankerAlice.signOut().get()
        }

        "Can create a valid group" {
            val aliceId = tc.createIdentity()
            val bobId = tc.createIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()

            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()

            tankerAlice.createGroup(Identity.getPublicIdentity(aliceId), Identity.getPublicIdentity(bobId)).get()

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can share to group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()

            val plaintext = "Two's company, three's a crowd"
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray()).get()
            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId), Identity.getPublicIdentity(bobId)).get()
            tankerAlice.share(arrayOf(tankerAlice.getResourceID(encrypted)), TankerShareOptions().shareWithGroups(groupId)).get()

            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can encrypt-and-share to group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()

            val plaintext = "Two's company, three's a crowd"
            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId), Identity.getPublicIdentity(bobId)).get()
            val encryptOptions = TankerEncryptOptions().shareWithGroups(groupId)
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()

            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can share to an external group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()

            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId)).get()

            val plaintext = "Two's company, three's a crowd"
            val encrypted = tankerBob.encrypt(plaintext.toByteArray()).get()
            tankerBob.share(arrayOf(tankerBob.getResourceID(encrypted)), TankerShareOptions().shareWithGroups(groupId)).get()

            String(tankerAlice.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can add a member to a group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()

            val plaintext = "Two's company, three's a crowd"
            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId)).get()
            val encryptOptions = TankerEncryptOptions().shareWithGroups(groupId)
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()

            tankerAlice.updateGroupMembers(groupId, usersToAdd = arrayOf(Identity.getPublicIdentity(bobId))).get()

            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
        }

        "Can transitively add members to a group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.signUp(aliceId).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.signUp(bobId).get()
            val charlieId = tc.createIdentity()
            val tankerCharlie = Tanker(options)
            tankerCharlie.signUp(charlieId).get()

            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(bobId)).get()
            tankerBob.updateGroupMembers(groupId, usersToAdd = arrayOf(Identity.getPublicIdentity(charlieId))).get()
            tankerCharlie.updateGroupMembers(groupId, usersToAdd = arrayOf(Identity.getPublicIdentity(aliceId))).get()

            val plaintext = "plain text"
            val encryptOptions = TankerEncryptOptions().shareWithGroups(groupId)
            val encrypted = tankerCharlie.encrypt(plaintext.toByteArray(), encryptOptions).get()
            String(tankerAlice.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.signOut().get()
            tankerBob.signOut().get()
            tankerCharlie.signOut().get()
        }
    }
}
