package io.tanker.bindings

/**
 * Tanker events that can be subscribed to
 * @see io.tanker.api.Tanker
 */
enum class TankerEvent(val value: Int) {
    SESSION_CLOSED(0),
    DEVICE_REVOKED(1),
}
