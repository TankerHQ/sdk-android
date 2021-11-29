package io.tanker.api

import io.tanker.api.errors.InvalidArgument
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GroupTests : TankerSpec() {
    @Test
    fun cannot_create_an_empty_group() {
        val aliceId = tc.createIdentity()
        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val e = shouldThrow<TankerFutureException> {
            tankerAlice.createGroup().get()
        }
        assertThat(e).hasCauseInstanceOf(InvalidArgument::class.java)

        tankerAlice.stop().get()
    }

    @Test
    fun can_create_a_valid_group() {
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

    @Test
    fun can_share_with_group() {
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

        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_encrypt_and_share_with_group() {
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

        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_share_with_an_external_group() {
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

        assertThat(String(tankerAlice.decrypt(encrypted).get())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_add_a_member_to_a_group() {
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

        assertThat(String(tankerBob.decrypt(encrypted).get())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun can_remove_a_member_from_a_group() {
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

        tankerAlice.updateGroupMembers(groupId, usersToAdd = arrayOf(), usersToRemove = arrayOf(Identity.getPublicIdentity(bobId))).get()

        val encryptOptions = EncryptionOptions().shareWithGroups(groupId)
        val encrypted = tankerAlice.encrypt(plaintext.toByteArray(), encryptOptions).get()

        val e = shouldThrow<TankerFutureException> {
            tankerBob.decrypt(encrypted).get()
        }
        assertThat(e).hasCauseInstanceOf(InvalidArgument::class.java)

        tankerAlice.stop().get()
        tankerBob.stop().get()
    }

    @Test
    fun cant_update_a_group_without_modification() {
        val aliceId = tc.createIdentity()
        val tankerAlice = Tanker(options)
        tankerAlice.start(aliceId).get()
        tankerAlice.registerIdentity(PassphraseVerification("pass")).get()

        val plaintext = "Two's company, three's a crowd"
        val groupId = tankerAlice.createGroup(Identity.getPublicIdentity(aliceId)).get()

        val e = shouldThrow<TankerFutureException> {
            tankerAlice.updateGroupMembers(groupId, usersToAdd = arrayOf(), usersToRemove = arrayOf()).get()
        }
        assertThat(e).hasCauseInstanceOf(InvalidArgument::class.java)

        tankerAlice.stop().get()
    }

    @Test
    fun can_transitively_add_members_to_a_group() {
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
        assertThat(String(tankerAlice.decrypt(encrypted).get())).isEqualTo(plaintext)

        tankerAlice.stop().get()
        tankerBob.stop().get()
        tankerCharlie.stop().get()
    }
}
