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

    class NestedUnion: Union() {
        @JvmField
        var verificationKey: String? = null
        @JvmField
        var emailVerification: TankerEmailVerification? = null
        @JvmField
        var passphrase: String? = null
    }

    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField
    var version: Byte = 1
    @JvmField
    var type: Byte = 0
    @JvmField
    var nestedUnion: NestedUnion = NestedUnion()

    override fun getFieldOrder(): List<String> {
        return listOf("version", "type", "nestedUnion")
    }
}

fun Verification.toCVerification(): TankerVerification {
    val out = TankerVerification()
    when (this) {
        is EmailVerification -> {
            out.type = TankerVerification.TypeEmail
            out.nestedUnion.setType("emailVerification")
            out.nestedUnion.emailVerification = TankerEmailVerification()
                    .setEmail(this.email)
                    .setVerificationCode(this.verificationCode)
        }
        is PassphraseVerification -> {
            out.type = TankerVerification.TypePassphrase
            out.nestedUnion.setType("passphrase")
            out.nestedUnion.passphrase = this.passphrase
        }
        is VerificationKeyVerification -> {
            out.type = TankerVerification.TypeVerificationKey
            out.nestedUnion.setType("verificationKey")
            out.nestedUnion.verificationKey = this.verificationKey
        }
    }
    return out
}
