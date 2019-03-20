package io.tanker.api

import io.kotlintest.Description
import io.kotlintest.shouldBe
import io.tanker.bindings.TankerUnlockMethod
import java.util.*


class UnlockTests : TankerSpec() {
    lateinit var userId: String
    lateinit var token: String
    lateinit var tanker1: Tanker
    lateinit var tanker2: Tanker

    override fun beforeTest(description: Description) {
        userId = tc.generateIdentity()
        token = userId
        tanker1 = Tanker(options.setWritablePath(createTmpDir().toString()))
        tanker2 = Tanker(options.setWritablePath(createTmpDir().toString()))
    }

    init {
        "Can validate a new device using a passphrase" {
            tanker1.signUp(token).get()
            val unlockKey = tanker1.generateAndRegisterUnlockKey().get()
            tanker1.signOut().get()
            tanker2.signIn(token, TankerSignInOptions().setUnlockKey(unlockKey)).get()
            tanker2.isOpen() shouldBe true
            tanker2.signOut().get()
        }

        "Can setup and use an unlock password" {
            val pass = "this is a password"

            tanker1.signUp(token).get()
            tanker1.isUnlockAlreadySetUp().get() shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            tanker1.signOut().get()

            tanker2.signIn(token, TankerSignInOptions().setPassword(pass)).get()
            tanker2.isOpen() shouldBe true
            tanker2.isUnlockAlreadySetUp().get() shouldBe true
            tanker2.signOut().get()
        }

        "Can update the unlock password" {
            val oldpass = "This is an old password"
            val newpass = "This is a new password"

            tanker1.signUp(token).get()
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(oldpass)).get()
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(newpass)).get()
            tanker1.signOut().get()

            tanker2.signIn(token, TankerSignInOptions().setPassword(newpass)).get()
            tanker2.signOut().get()
        }

        "Alice's second device can decrypt old resources" {
            val pass = "This is a strong password"
            val plainText = "plain text"

            tanker1.signUp(token).get()
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            val secret = tanker1.encrypt(plainText.toByteArray()).get()
            tanker1.signOut().get()

            tanker2.signIn(token, TankerSignInOptions().setPassword(pass)).get()
            String(tanker2.decrypt(secret).get()) shouldBe plainText
            tanker2.signOut().get()
        }

        "hasRegisteredUnlockMethods return true iff unlock is set-up" {
            val pass = "this is a password"

            tanker1.signUp(token).get()
            tanker1.hasRegisteredUnlockMethods() shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            tanker1.hasRegisteredUnlockMethods() shouldBe true
            tanker1.signOut().get()
        }

        "Can check that the password unlock method is set-up" {
            val pass = "this is a password"

            tanker1.signUp(token).get()
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.PASSWORD) shouldBe false
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.EMAIL) shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.PASSWORD) shouldBe true
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.EMAIL) shouldBe false
            tanker1.signOut().get()
        }

        "Can check that the email unlock method is set-up" {
            val email = "bob@wonderland.io"

            tanker1.signUp(token).get()
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.PASSWORD) shouldBe false
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.EMAIL) shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setEmail(email)).get()
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.PASSWORD) shouldBe false
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.EMAIL) shouldBe true
            tanker1.signOut().get()
        }

        "Can get the list of unlock methods that have been set-up" {
            val pass = "this is a password"
            val email = "bob@wonderland.io"

            tanker1.signUp(token).get()
            tanker1.registeredUnlockMethods().size shouldBe 0
            tanker1.registerUnlock(TankerUnlockOptions().setEmail(email).setPassword(pass)).get()
            val unlockMethods = tanker1.registeredUnlockMethods()
            unlockMethods.size shouldBe 2
            unlockMethods[0].type shouldBe TankerUnlockMethod.EMAIL
            unlockMethods[1].type shouldBe TankerUnlockMethod.PASSWORD
            tanker1.signOut().get()
        }

        "Can use registerUnlock to setup and update an unlock method" {
            val email = "bob@wonderland.io"
            val oldpass = "this is an old password"
            val newpass = "this is a new password"

            tanker1.signUp(token).get()
            tanker1.registeredUnlockMethods().size shouldBe 0
            tanker1.registerUnlock(TankerUnlockOptions().setEmail(email).setPassword(oldpass)).get()
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(newpass)).get()
            tanker1.signOut().get()

            tanker2.signIn(token, TankerSignInOptions().setPassword(newpass)).get()
            tanker2.signOut().get()
        }
    }
}
