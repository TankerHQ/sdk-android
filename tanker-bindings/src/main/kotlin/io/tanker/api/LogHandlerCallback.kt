package io.tanker.api

import com.sun.jna.Callback

interface LogHandlerCallback : Callback {
    fun callback(logRecord: LogRecord)
}
