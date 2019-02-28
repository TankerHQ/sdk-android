package io.tanker.api

enum class TankerSignInResult(val value: Int) {
    OK(0),
    IDENTITY_NOT_REGISTERED(1),
    IDENTITY_VERIFICATION_NEEDED(2);

    companion object {
        private val map = TankerSignInResult.values().associateBy(TankerSignInResult::value)
        fun fromInt(type: Int) = map[type]
    }
}