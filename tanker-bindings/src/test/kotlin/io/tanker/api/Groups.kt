package io.tanker.api

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow

class GroupTests : TankerSpec() {

    init {
        "Cannot create an empty group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

            val e = shouldThrow<TankerFutureException> {
                tankerAlice.createGroup().get()
            }
            (e.cause is TankerException) shouldBe true
            (e.cause as TankerException).errorCode shouldBe ErrorCode.INVALID_ARGUMENT

            tankerAlice.stop().get()
        }

        "Can create a valid group" {
            val aliceId = tc.createIdentity()
            val bobId = tc.createIdentity()

            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            tankerAlice.createGroup(Identity.getPublicIdentity(aliceId), Identity.getPublicIdentity(bobId)).get()

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can share to group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val plaintext = "Two's company, three's a crowd"
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray()).get()
            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId), Identity.getPublicIdentity(bobId)).get()
            tankerAlice.share(arrayOf(tankerAlice.getResourceID(encrypted)), SharingOptions().shareWithGroups(groupId)).get()

            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can encrypt-and-share to group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val plaintext = "Two's company, three's a crowd"
            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId), Identity.getPublicIdentity(bobId)).get()
            val encryptOptions = EncryptionOptions().shareWithGroups(groupId)
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()

            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can share to an external group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId)).get()

            val plaintext = "Two's company, three's a crowd"
            val encrypted = tankerBob.encrypt(plaintext.toByteArray()).get()
            tankerBob.share(arrayOf(tankerBob.getResourceID(encrypted)), SharingOptions().shareWithGroups(groupId)).get()

            String(tankerAlice.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can add a member to a group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()

            val plaintext = "Two's company, three's a crowd"
            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId)).get()
            val encryptOptions = EncryptionOptions().shareWithGroups(groupId)
            val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()

            tankerAlice.updateGroupMembers(groupId, usersToAdd = arrayOf(Identity.getPublicIdentity(bobId))).get()

            String(tankerBob.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
        }

        "Can transitively add members to a group" {
            val aliceId = tc.createIdentity()
            val tankerAlice = Tanker(options)
            tankerAlice.start(aliceId).get()
            tankerAlice.registerIdentity(PassphraseVerification("pass")).get()
            val bobId = tc.createIdentity()
            val tankerBob = Tanker(options)
            tankerBob.start(bobId).get()
            tankerBob.registerIdentity(PassphraseVerification("pass")).get()
            val charlieId = tc.createIdentity()
            val tankerCharlie = Tanker(options)
            tankerCharlie.start(charlieId).get()
            tankerCharlie.registerIdentity(PassphraseVerification("pass")).get()

            val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(bobId)).get()
            tankerBob.updateGroupMembers(groupId, usersToAdd = arrayOf(Identity.getPublicIdentity(charlieId))).get()
            tankerCharlie.updateGroupMembers(groupId, usersToAdd = arrayOf(Identity.getPublicIdentity(aliceId))).get()

            val plaintext = "plain text"
            val encryptOptions = EncryptionOptions().shareWithGroups(groupId)
            val encrypted = tankerCharlie.encrypt(plaintext.toByteArray(), encryptOptions).get()
            String(tankerAlice.decrypt(encrypted).get()) shouldBe plaintext

            tankerAlice.stop().get()
            tankerBob.stop().get()
            tankerCharlie.stop().get()
        }
    }
}
