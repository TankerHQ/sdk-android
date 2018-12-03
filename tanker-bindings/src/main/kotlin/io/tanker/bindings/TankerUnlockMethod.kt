package io.tanker.bindings

/**
 * Unlocks methods that can be registered
 * NOTE: This enum is a bitset
 * @see Tanker
 */
enum class TankerUnlockMethod(val value: Int) {
    EMAIL(1 shl 0),
    PASSWORD(1 shl 1),
}