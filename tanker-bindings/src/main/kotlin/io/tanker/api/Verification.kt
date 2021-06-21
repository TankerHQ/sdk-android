package io.tanker.api

sealed class Verification
data class EmailVerification(val email: String, val verificationCode: String) : Verification()
data class PassphraseVerification(val passphrase: String) : Verification()
data class VerificationKeyVerification(val verificationKey: String) : Verification()
data class OIDCIDTokenVerification(val oidcIDToken: String) : Verification()
data class PhoneNumberVerification(val phoneNumber: String, val verificationCode: String) : Verification()
