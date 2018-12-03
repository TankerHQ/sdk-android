package io.tanker.bindings

import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * Raw errors returned by native tanker futures
 *
 * Please use the getter methods instead of accessing the fields directly.
 * Those are not part of the public API and are subject to change.
 */
class TankerError : Structure() {
    @JvmField val errorCode = TankerErrorCode.NO_ERROR.value
    @JvmField val errorMessage = Pointer(0)

    companion object {
        private val lib = TankerLib.create()
    }

    fun getErrorCode(): TankerErrorCode {
        return TankerErrorCode.values().find { it.value == errorCode }!!
    }

    fun getErrorMessage(): String {
        return errorMessage.getString(0)
    }

    override fun getFieldOrder(): List<String> {
        return listOf("errorCode", "errorMessage")
    }
}
