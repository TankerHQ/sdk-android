package io.tanker.api

class UnlockKey constructor(private val unlockKey: String) {

    fun string(): String {
        return unlockKey
    }
}