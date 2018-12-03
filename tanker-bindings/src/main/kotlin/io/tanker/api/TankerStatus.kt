package io.tanker.api

/**
 * Represents the state of Tanker when trying to open a session
 * @see Tanker
 */
enum class TankerStatus(val value: Int) {
    IDLE(0),
    OPEN(1),
    USER_CREATION(2),
    DEVICE_CREATION(3),
    CLOSING(4),
}
