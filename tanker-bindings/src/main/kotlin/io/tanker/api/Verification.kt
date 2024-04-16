package io.tanker.api

sealed class Verification
data class EmailVerification(val email: String, val verificationCode: String) : Verification()
data class PassphraseVerification(val passphrase: String) : Verification()
data class VerificationKeyVerification(val verificationKey: String) : Verification()
data class OIDCIDTokenVerification(val oidcIDToken: String) : Verification()
data class PhoneNumberVerification(val phoneNumber: String, val verificationCode: String) : Verification()
data class PreverifiedEmailVerification(val preverifiedEmail: String) : Verification()
data class PreverifiedPhoneNumberVerification(val preverifiedPhoneNumber: String) : Verification()
data class E2ePassphraseVerification(val e2ePassphrase: String) : Verification()
data class PreverifiedOIDCVerification(val subject: String, val providerID: String) : Verification()
data class OIDCAuthorizationCodeVerification(val providerID: String, val authorizationCode: String, val state: String) : Verification()
