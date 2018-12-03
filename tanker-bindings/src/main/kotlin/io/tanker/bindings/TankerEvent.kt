package io.tanker.bindings

/**
 * Tanker events that can be subscribed to
 * @see Tanker
 */
enum class TankerEvent(val value: Int) {
    // Event 0 is unused
    SESSION_CLOSED(1),
    DEVICE_CREATED(2),
    UNLOCK_REQUIRED(3),
}