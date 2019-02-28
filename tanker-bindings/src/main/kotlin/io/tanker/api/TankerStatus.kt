package io.tanker.api

/**
 * Represents the state of Tanker when trying to open a session
 * @see Tanker
 */
enum class TankerStatus(val value: Int) {
    CLOSED(0),
    OPEN(1),
}
