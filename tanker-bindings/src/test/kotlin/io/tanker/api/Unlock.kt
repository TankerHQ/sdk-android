package io.tanker.api

import io.kotlintest.Description
import io.kotlintest.shouldBe


class UnlockTests : TankerSpec() {
    lateinit var userId: String
    lateinit var identity: String
    lateinit var tanker1: Tanker
    lateinit var tanker2: Tanker

    override fun beforeTest(description: Description) {
        identity = tc.createIdentity()
        tanker1 = Tanker(options.setWritablePath(createTmpDir().toString()))
        tanker2 = Tanker(options.setWritablePath(createTmpDir().toString()))
    }

    init {
        "Can validate a new device using a verification key" {
            tanker1.start(identity).get()
            val verificationKey = tanker1.generateVerificationKey().get()
            tanker1.registerIdentity(VerificationKeyVerification(verificationKey)).get()
            tanker1.stop().get()
            tanker2.start(identity).get()
            tanker2.verifyIdentity(VerificationKeyVerification(verificationKey)).get()
            tanker2.getStatus() shouldBe TankerStatus.READY
            tanker2.stop().get()
        }

        "Can setup and use an unlock password" {
            val pass = "this is a password"

            tanker1.start(identity).get()
            tanker1.registerIdentity(PassphraseVerification(pass)).get()
            tanker1.stop().get()

            tanker2.start(identity).get()
            tanker2.getStatus() shouldBe TankerStatus.IDENTITY_VERIFICATION_NEEDED
            tanker2.verifyIdentity(PassphraseVerification(pass)).get()
            tanker2.getStatus() shouldBe TankerStatus.READY
            tanker2.stop().get()
        }

        "Can update the unlock password" {
            val oldpass = "This is an old password"
            val newpass = "This is a new password"

            tanker1.start(identity).get()
            tanker1.registerIdentity(PassphraseVerification(oldpass)).get()
            tanker1.setVerificationMethod(PassphraseVerification(newpass)).get()
            tanker1.stop().get()

            tanker2.start(identity).get()
            tanker2.verifyIdentity(PassphraseVerification(newpass)).get()
            tanker2.stop().get()
        }

        "Alice's second device can decrypt old resources" {
            val pass = "This is a strong password"
            val plainText = "plain text"

            tanker1.start(identity).get()
            tanker1.registerIdentity(PassphraseVerification(pass)).get()
            val secret = tanker1.encrypt(plainText.toByteArray()).get()
            tanker1.stop().get()

            tanker2.start(identity).get()
            tanker2.verifyIdentity(PassphraseVerification(pass)).get()
            String(tanker2.decrypt(secret).get()) shouldBe plainText
            tanker2.stop().get()
        }

        "Can check that the password unlock method is set-up" {
            val pass = "this is a password"

            tanker1.start(identity).get()
            tanker1.registerIdentity(PassphraseVerification(pass)).get()
            val methods = tanker1.getVerificationMethods().get()
            methods shouldBe arrayOf(PassphraseVerificationMethod)
            tanker1.stop().get()
        }

        "Can check that the email unlock method is set-up" {
            val email = "bob@wonderland.io"

            tanker1.start(identity).get()
            val verificationCode = tc.admin.getVerificationCode(tc.id(), email).get()
            tanker1.registerIdentity(EmailVerification(email, verificationCode)).get()
            val methods = tanker1.getVerificationMethods().get()
            methods shouldBe arrayOf(EmailVerificationMethod(email))
            tanker1.stop().get()
        }

        "Can get the list of unlock methods that have been set-up" {
            val pass = "this is a password"
            val email = "bob@wonderland.io"

            tanker1.start(identity).get()
            tanker1.registerIdentity(PassphraseVerification(pass)).get()
            tanker1.getVerificationMethods().get().size shouldBe 1
            val verificationCode = tc.admin.getVerificationCode(tc.id(), email).get()
            tanker1.setVerificationMethod(EmailVerification(email, verificationCode)).get()
            val methods = tanker1.getVerificationMethods().get()
            methods.size shouldBe 2
            tanker1.stop().get()
        }

        "Can use setVerificationMethod to setup and update an unlock method" {
            val email = "bob@wonderland.io"
            val oldpass = "this is an old password"
            val newpass = "this is a new password"

            tanker1.start(identity).get()
            val verificationCode = tc.admin.getVerificationCode(tc.id(), email).get()
            tanker1.registerIdentity(EmailVerification(email, verificationCode)).get()
            tanker1.getVerificationMethods().get().size shouldBe 1
            tanker1.setVerificationMethod(PassphraseVerification(oldpass)).get()
            tanker1.setVerificationMethod(PassphraseVerification(newpass)).get()
            tanker1.getVerificationMethods().get().size shouldBe 2
            tanker1.stop().get()

            tanker2.start(identity).get()
            tanker2.verifyIdentity(PassphraseVerification(newpass)).get()
            tanker2.stop().get()
        }

        "Can unlock with a verification code" {
            val email = "bob@wonderland.io"

            tanker1.start(identity).get()
            var verificationCode = tc.admin.getVerificationCode(tc.id(), email).get()
            tanker1.registerIdentity(EmailVerification(email, verificationCode)).get()

            tanker2.start(identity).get()
            verificationCode = tc.admin.getVerificationCode(tc.id(), email).get()
            tanker2.verifyIdentity(EmailVerification(email, verificationCode)).get()
            tanker2.getStatus() shouldBe TankerStatus.READY
        }
    }
}
