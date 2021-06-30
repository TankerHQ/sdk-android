package io.tanker.api

import io.tanker.bindings.TankerVerification
import io.tanker.bindings.TankerVerificationMethod

sealed class VerificationMethod
data class EmailVerificationMethod(val email: String) : VerificationMethod()
object PassphraseVerificationMethod : VerificationMethod()
object VerificationKeyVerificationMethod : VerificationMethod()
object OIDCIDTokenVerificationMethod : VerificationMethod()
data class PhoneNumberVerificationMethod(val phoneNumber: String) : VerificationMethod()

fun verificationMethodFromCVerification(method: TankerVerificationMethod) =
        when (method.type) {
            TankerVerification.TypeEmail -> EmailVerificationMethod(method.value!!)
            TankerVerification.TypeVerificationKey -> VerificationKeyVerificationMethod
            TankerVerification.TypePassphrase -> PassphraseVerificationMethod
            TankerVerification.TypeOIDCIDToken -> OIDCIDTokenVerificationMethod
            TankerVerification.TypePhoneNumber -> PhoneNumberVerificationMethod(method.value!!)
            else -> throw RuntimeException("unknown verification method type: ${method.type}")
        }
