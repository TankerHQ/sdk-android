package io.tanker.api

import io.tanker.bindings.TankerVerification
import io.tanker.bindings.TankerVerificationMethod

sealed class VerificationMethod
data class EmailVerificationMethod(val email: String) : VerificationMethod()
object PassphraseVerificationMethod : VerificationMethod()
object VerificationKeyVerificationMethod : VerificationMethod()

fun verificationMethodFromCVerification(method: TankerVerificationMethod) =
        when (method.type) {
            TankerVerification.TypeEmail -> EmailVerificationMethod(method.email!!)
            TankerVerification.TypeVerificationKey -> VerificationKeyVerificationMethod
            TankerVerification.TypePassphrase -> PassphraseVerificationMethod
            else -> throw RuntimeException("unknown verification method type: ${method.type}")
        }
