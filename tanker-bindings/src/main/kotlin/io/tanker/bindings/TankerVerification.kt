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
        const val TypeE2ePassphrase: Byte = 8
        const val TypePreverifiedOIDC: Byte = 9
        const val TypeOIDCAuthorizationCode: Byte = 10
        const val TypePrehashedAndEncryptedPassphrase: Byte = 11
    }

    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField
    var version: Byte = 9
    @JvmField
    var type: Byte = 0
    @JvmField
    var verificationKey: String? = null
    @JvmField
    var emailVerification: TankerEmailVerification? = null
    @JvmField
    var passphrase: String? = null
    @JvmField
    var e2ePassphrase: String? = null
    @JvmField
    var oidcIDToken: String? = null
    @JvmField
    var phoneNumberVerification: TankerPhoneNumberVerification? = null
    @JvmField
    var preverifiedEmail: String? = null
    @JvmField
    var preverifiedPhoneNumber: String? = null
    @JvmField
    var preverifiedOIDCVerification: TankerPreverifiedOIDCVerification? = null
    @JvmField
    var oidcAuthorizationCodeVerification: TankerOIDCAuthorizationCodeVerification? = null
    @JvmField
    var prehashedAndEncryptedPassphrase: String? = null

    override fun getFieldOrder(): List<String> {
        return listOf("version", "type", "verificationKey", "emailVerification", "passphrase", "e2ePassphrase", "oidcIDToken", "phoneNumberVerification", "preverifiedEmail", "preverifiedPhoneNumber", "preverifiedOIDCVerification", "oidcAuthorizationCodeVerification", "prehashedAndEncryptedPassphrase")
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
        is E2ePassphraseVerification -> {
            out.type = TankerVerification.TypeE2ePassphrase
            out.e2ePassphrase = this.e2ePassphrase
        }
        is PreverifiedOIDCVerification -> {
            out.type = TankerVerification.TypePreverifiedOIDC
            out.preverifiedOIDCVerification = TankerPreverifiedOIDCVerification()
                .setSubject(this.subject)
                .setProviderID(this.providerID)
        }
        is OIDCAuthorizationCodeVerification -> {
            out.type = TankerVerification.TypeOIDCAuthorizationCode
            out.oidcAuthorizationCodeVerification = TankerOIDCAuthorizationCodeVerification()
                .setProviderID(this.providerID)
                .setAuthorizationCode(this.authorizationCode)
                .setState(this.state)
        }
        is PrehashedAndEncryptedPassphraseVerification -> {
            out.type = TankerVerification.TypePrehashedAndEncryptedPassphrase
            out.prehashedAndEncryptedPassphrase = this.prehashedAndEncryptedPassphrase
        }
    }
    return out
}
