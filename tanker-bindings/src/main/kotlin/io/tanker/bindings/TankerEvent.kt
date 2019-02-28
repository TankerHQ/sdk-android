package io.tanker.bindings

/**
 * Tanker events that can be subscribed to
 * @see Tanker
 */
enum class TankerEvent(val value: Int) {
    SESSION_CLOSED(0),
    DEVICE_CREATED(1),
    DEVICE_REVOKED(2),
}