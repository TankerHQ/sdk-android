package io.tanker.api

import org.assertj.core.api.Assertions.fail
import org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown

inline fun<reified T : Throwable> shouldThrow(code: () -> Unit): Exception {
    try {
        code()
        failBecauseExceptionWasNotThrown(T::class.java)
    } catch (e: Exception) {
        if (e::class != T::class)
            fail("Expected exception ${T::class} but got $e")
        return e
    }
    throw RuntimeException("unreachable code")
}
