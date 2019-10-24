package io.tanker.api

/**
 * Represents the state of Tanker when trying to open a session
 * @see Tanker
 */
enum class Status(val value: Int) {
    STOPPED(0),
    READY(1),
    IDENTITY_REGISTRATION_NEEDED(2),
    IDENTITY_VERIFICATION_NEEDED(3);

    companion object {
        private val map = values().associateBy(Status::value)
        fun fromInt(type: Int) = map.getValue(type)
    }
}
