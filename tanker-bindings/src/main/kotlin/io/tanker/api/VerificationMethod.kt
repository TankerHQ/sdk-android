package io.tanker.api

import io.tanker.bindings.TankerVerification
import io.tanker.bindings.TankerVerificationMethod

sealed class VerificationMethod
data class EmailVerificationMethod(val email: String) : VerificationMethod()
object PassphraseVerificationMethod : VerificationMethod()
object VerificationKeyVerificationMethod : VerificationMethod()
data class OIDCIDTokenVerificationMethod(val providerId: String, val providerDisplayName: String) : VerificationMethod()
data class PhoneNumberVerificationMethod(val phoneNumber: String) : VerificationMethod()
data class PreverifiedEmailVerificationMethod(val preverifiedEmail: String) : VerificationMethod()
data class PreverifiedPhoneNumberVerificationMethod(val preverifiedPhoneNumber: String): VerificationMethod()
object E2ePassphraseVerificationMethod : VerificationMethod()

fun verificationMethodFromCVerification(method: TankerVerificationMethod) =
        when (method.type) {
            TankerVerification.TypeEmail -> EmailVerificationMethod(method.value1!!)
            TankerVerification.TypeVerificationKey -> VerificationKeyVerificationMethod
            TankerVerification.TypePassphrase -> PassphraseVerificationMethod
            TankerVerification.TypeOIDCIDToken -> OIDCIDTokenVerificationMethod(method.value1!!, method.value2!!)
            TankerVerification.TypePhoneNumber -> PhoneNumberVerificationMethod(method.value1!!)
            TankerVerification.TypePreverifiedEmail -> PreverifiedEmailVerificationMethod(method.value1!!)
            TankerVerification.TypePreverifiedPhoneNumber -> PreverifiedPhoneNumberVerificationMethod(method.value1!!)
            TankerVerification.TypeE2ePassphrase -> E2ePassphraseVerificationMethod
            else -> throw RuntimeException("unknown verification method type: ${method.type}")
        }
