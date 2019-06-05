package io.tanker.api

/**
 * Represents the state of Tanker when trying to open a session
 * @see Tanker
 */
enum class TankerStatus(val value: Int) {
    STOPPED(0),
    READY(1),
    IDENTITY_REGISTRATION_NEEDED(2),
    IDENTITY_VERIFICATION_NEEDED(3);

    companion object {
        private val map = TankerStatus.values().associateBy(TankerStatus::value)
        fun fromInt(type: Int) = map[type]
    }
}
