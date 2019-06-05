package io.tanker.api

sealed class VerificationMethod
data class EmailVerificationMethod(val email: String): VerificationMethod()
object PassphraseVerificationMethod: VerificationMethod()
object VerificationKeyVerificationMethod: VerificationMethod()