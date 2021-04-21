package io.tanker.bindings

import com.sun.jna.Pointer
import com.sun.jna.Structure

class TankerHttpResponse : Structure() {
    @JvmField var errorMsg: String? = null
    @JvmField var contentType: String? = null
    @JvmField var body: Pointer? = null
    @JvmField var bodySize: Long = 0
    @JvmField var statusCode: Int = 0

    override fun getFieldOrder(): List<String> {
        return listOf("errorMsg", "contentType", "body", "bodySize", "statusCode")
    }
}
