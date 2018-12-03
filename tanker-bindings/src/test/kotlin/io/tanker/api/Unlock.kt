package io.tanker.api

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.StringSpec
import io.kotlintest.TestCaseConfig
import io.kotlintest.seconds
import io.tanker.bindings.TankerErrorCode
import io.tanker.bindings.TankerLib
import io.tanker.bindings.TankerUnlockMethod
import java.util.*


class UnlockTests : StringSpec() {
    private val options = TankerOptions()
    private val userId: String
    private val token: String
    private val tanker1: Tanker
    private val tanker2: Tanker
    override val defaultTestCaseConfig = TestCaseConfig(timeout = 30.seconds)

    init {
        val tc = TestTrustchain.get()
        options.setTrustchainId(tc.id())
                .setTrustchainUrl(tc.url)
                .setWritablePath(createTmpDir().toString())
        setupTestEnv()

        userId = UUID.randomUUID().toString()
        token = tc.generateUserToken(userId)
        tanker1 = Tanker(options.setWritablePath(createTmpDir().toString()))
        tanker2 = Tanker(options.setWritablePath(createTmpDir().toString()))
    }

    init {
        "Can validate a new device using a passphrase" {
            tanker1.open(userId, token).get()
            val unlockKey = tanker1.generateAndRegisterUnlockKey().get()
            tanker1.close().get()
            tanker2.connectUnlockRequiredHandler(TankerUnlockRequiredHandler{
                tanker2.unlockCurrentDeviceWithUnlockKey(unlockKey).get()
            })
            tanker2.open(userId, token).get()
            tanker2.getStatus() shouldEqual TankerStatus.OPEN
            tanker2.close().get()
        }

        "Can validate a new device with a passphrase using the deprecated API" {
            tanker1.open(userId, token).get()
            val unlockKey = tanker1.generateAndRegisterUnlockKey().get()
            tanker1.close().get()
            tanker2.connectUnlockRequiredHandler(TankerUnlockRequiredHandler{
                @Suppress("DEPRECATION")
                tanker2.unlockCurrentDevice(UnlockKey(unlockKey)).get()
            })
            tanker2.open(userId, token).get()
            tanker2.getStatus() shouldEqual TankerStatus.OPEN
            tanker2.close().get()
        }

        "Can setup and use an unlock password" {
            val pass = "this is a password"

            tanker1.open(userId, token).get()
            tanker1.isUnlockAlreadySetUp().get() shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            tanker1.close().get()

            tanker2.connectUnlockRequiredHandler(TankerUnlockRequiredHandler{
                tanker2.unlockCurrentDeviceWithPassword(pass).get()
            })
            tanker2.open(userId, token).get()
            tanker2.getStatus() shouldEqual TankerStatus.OPEN
            tanker2.isUnlockAlreadySetUp().get() shouldBe true
            tanker2.close().get()
        }

        "Can unlock with a password using the deprecated API" {
            val pass = "this is a password"

            tanker1.open(userId, token).get()
            tanker1.isUnlockAlreadySetUp().get() shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            tanker1.close().get()

            tanker2.connectUnlockRequiredHandler(TankerUnlockRequiredHandler{
                @Suppress("DEPRECATION")
                tanker2.unlockCurrentDevice(Password(pass)).get()
            })
            tanker2.open(userId, token).get()
            tanker2.getStatus() shouldEqual TankerStatus.OPEN
            tanker2.isUnlockAlreadySetUp().get() shouldBe true
            tanker2.close().get()
        }

        @Suppress("DEPRECATION")
        "Cannot setup unlock several times" {
            val pass = Password("*******")
            tanker1.open(userId, token).get()
            tanker1.setupUnlock(password = pass).get()

            val otherPass = Password("p4ssw0rd")
            val e = shouldThrow<TankerFutureException> {
                tanker1.setupUnlock(password = otherPass).get()
            }
            (e.cause is TankerException) shouldBe true
            (e.cause as TankerException).errorCode shouldBe TankerErrorCode.UNLOCK_KEY_ALREADY_EXISTS
            tanker1.close().get()
        }

        @Suppress("DEPRECATION")
        "Can update the unlock password" {
            val oldpass = Password("This is an old password")
            val newpass = Password("This is a new password")

            tanker1.open(userId, token).get()
            tanker1.setupUnlock(password = oldpass).get()
            tanker1.updateUnlock(password = newpass).get()
            tanker1.close().get()

            tanker2.connectUnlockRequiredHandler(TankerUnlockRequiredHandler{
                tanker2.unlockCurrentDeviceWithPassword(newpass.string()).get()
            })
            tanker2.open(userId, token).get()
            tanker2.close().get()
        }

        @Suppress("DEPRECATION")
        "Cannot update the unlock password before setting it up" {
            val newpass = Password("Anachronique")

            val tanker1 = Tanker(options.setWritablePath(createTmpDir().toString()))
            tanker1.open(userId, token).get()
            val e = shouldThrow<TankerFutureException> {
                tanker1.updateUnlock(password = newpass).get()
            }
            (e.cause is TankerException) shouldBe true
            (e.cause as TankerException).errorCode shouldBe TankerErrorCode.INVALID_UNLOCK_KEY
            tanker1.close().get()
        }

        "Alice's second device can decrypt old resources" {
            val pass = "This is a strong password"
            val plainText = "plain text"

            tanker1.open(userId, token).get()
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            val secret = tanker1.encrypt(plainText.toByteArray()).get()
            tanker1.close().get()

            tanker2.connectUnlockRequiredHandler(TankerUnlockRequiredHandler{
                tanker2.unlockCurrentDeviceWithPassword(pass).get()
            })
            tanker2.open(userId, token).get()
            String(tanker2.decrypt(secret).get()) shouldEqual plainText
            tanker2.close().get()
        }

        "hasRegisteredUnlockMethods return true iff unlock is set-up" {
            val pass = "this is a password"

            tanker1.open(userId, token).get()
            tanker1.hasRegisteredUnlockMethods() shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            tanker1.hasRegisteredUnlockMethods() shouldBe true
            tanker1.close().get()
        }

        "Can check that the password unlock method is set-up" {
            val pass = "this is a password"

            tanker1.open(userId, token).get()
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.PASSWORD) shouldBe false
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.EMAIL) shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(pass)).get()
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.PASSWORD) shouldBe true
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.EMAIL) shouldBe false
            tanker1.close().get()
        }

        "Can check that the email unlock method is set-up" {
            val email = "bob@wonderland.io"

            tanker1.open(userId, token).get()
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.PASSWORD) shouldBe false
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.EMAIL) shouldBe false
            tanker1.registerUnlock(TankerUnlockOptions().setEmail(email)).get()
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.PASSWORD) shouldBe false
            tanker1.hasRegisteredUnlockMethod(TankerUnlockMethod.EMAIL) shouldBe true
            tanker1.close().get()
        }

        "Can get the list of unlock methods that have been set-up" {
            val pass = "this is a password"
            val email = "bob@wonderland.io"

            tanker1.open(userId, token).get()
            tanker1.registeredUnlockMethods().size shouldBe 0
            tanker1.registerUnlock(TankerUnlockOptions().setEmail(email).setPassword(pass)).get()
            val unlockMethods = tanker1.registeredUnlockMethods()
            unlockMethods.size shouldBe 2
            unlockMethods[0].type shouldBe TankerUnlockMethod.EMAIL
            unlockMethods[1].type shouldBe TankerUnlockMethod.PASSWORD
            tanker1.close().get()
        }

        "Can use registerUnlock to setup and update an unlock method" {
            val email = "bob@wonderland.io"
            val oldpass = "this is an old password"
            val newpass = "this is a new password"

            tanker1.open(userId, token).get()
            tanker1.registeredUnlockMethods().size shouldBe 0
            tanker1.registerUnlock(TankerUnlockOptions().setEmail(email).setPassword(oldpass)).get()
            tanker1.registerUnlock(TankerUnlockOptions().setPassword(newpass)).get()
            tanker1.close().get()

            tanker2.connectUnlockRequiredHandler(TankerUnlockRequiredHandler{
                tanker2.unlockCurrentDeviceWithPassword(newpass).get()
            })
            tanker2.open(userId, token).get()
            tanker2.close().get()
        }
    }
}
