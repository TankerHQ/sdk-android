package io.tanker.bindings

import com.sun.jna.Structure
import com.sun.jna.Union
import io.tanker.api.EmailVerification
import io.tanker.api.PassphraseVerification
import io.tanker.api.Verification
import io.tanker.api.VerificationKeyVerification

class TankerVerification : Structure() {
    companion object {
        const val TypeEmail: Byte = 1
        const val TypePassphrase: Byte = 2
        const val TypeVerificationKey: Byte = 3
    }

    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField
    var version: Byte = 2
    @JvmField
    var type: Byte = 0
    @JvmField
    var verificationKey: String? = null
    @JvmField
    var emailVerification: TankerEmailVerification? = null
    @JvmField
    var passphrase: String? = null

    override fun getFieldOrder(): List<String> {
        return listOf("version", "type", "verificationKey", "emailVerification", "passphrase")
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
    }
    return out
}
