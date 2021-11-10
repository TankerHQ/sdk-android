package io.tanker.bindings

import com.sun.jna.Structure
import io.tanker.api.*

class TankerVerification : Structure() {
    companion object {
        const val TypeEmail: Byte = 1
        const val TypePassphrase: Byte = 2
        const val TypeVerificationKey: Byte = 3
        const val TypeOIDCIDToken: Byte = 4
        const val TypePhoneNumber: Byte = 5
        const val TypePreverifiedEmail: Byte = 6
        const val TypePreverifiedPhoneNumber: Byte = 7
    }

    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField
    var version: Byte = 5
    @JvmField
    var type: Byte = 0
    @JvmField
    var verificationKey: String? = null
    @JvmField
    var emailVerification: TankerEmailVerification? = null
    @JvmField
    var passphrase: String? = null
    @JvmField
    var oidcIDToken: String? = null
    @JvmField
    var phoneNumberVerification: TankerPhoneNumberVerification? = null
    @JvmField
    var preverifiedEmail: String? = null
    @JvmField
    var preverifiedPhoneNumber: String? = null

    override fun getFieldOrder(): List<String> {
        return listOf("version", "type", "verificationKey", "emailVerification", "passphrase", "oidcIDToken", "phoneNumberVerification", "preverifiedEmail", "preverifiedPhoneNumber")
    }
}

fun Verification.toCVerification(): TankerVerification {
    val out = TankerVerification()
    when (this) {
        is EmailVerification -> {
            out.type = TankerVerification.TypeEmail
            out.emailVerification = TankerEmailVerification()
                    .setEmail(this.email)
                    .setVerificationCode(this.verificationCode)
        }
        is PassphraseVerification -> {
            out.type = TankerVerification.TypePassphrase
            out.passphrase = this.passphrase
        }
        is VerificationKeyVerification -> {
            out.type = TankerVerification.TypeVerificationKey
            out.verificationKey = this.verificationKey
        }
        is OIDCIDTokenVerification -> {
            out.type = TankerVerification.TypeOIDCIDToken
            out.oidcIDToken = this.oidcIDToken
        }
        is PhoneNumberVerification -> {
            out.type = TankerVerification.TypePhoneNumber
            out.phoneNumberVerification = TankerPhoneNumberVerification()
                    .setPhoneNumber(this.phoneNumber)
                    .setVerificationCode(this.verificationCode)
        }
        is PreverifiedEmailVerification -> {
            out.type = TankerVerification.TypePreverifiedEmail
            out.preverifiedEmail = this.preverifiedEmail
        }
        is PreverifiedPhoneNumberVerification -> {
            out.type = TankerVerification.TypePreverifiedPhoneNumber
            out.preverifiedPhoneNumber = this.preverifiedPhoneNumber
        }
    }
    return out
}
