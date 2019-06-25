package io.tanker.api

import com.sun.jna.Structure

enum class TankerLogLevel(val value: Int) {
    DEBUG(1), INFO(2), WARNING(3), ERROR(4)
}

class LogRecord : Structure() {
    @JvmField
    var category: String? = null
    @JvmField
    var level: Int = 0
    @JvmField
    var file: String? = null
    @JvmField
    var line: Int = 0
    @JvmField
    var message: String? = null

    override fun getFieldOrder(): List<String> {
        return listOf("category", "level", "file", "line", "message")
    }
}
