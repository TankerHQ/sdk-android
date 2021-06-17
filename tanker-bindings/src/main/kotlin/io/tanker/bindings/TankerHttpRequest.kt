package io.tanker.bindings

import com.sun.jna.Pointer
import com.sun.jna.Structure

class TankerHttpRequest : Structure() {
    @JvmField var method: String? = null
    @JvmField var url: String? = null
    @JvmField var instanceId: String? = null
    @JvmField var authorization: String? = null
    @JvmField var body: Pointer? = null
    @JvmField var bodySize: Int = 0

    override fun getFieldOrder(): List<String> {
        return listOf("method", "url", "instanceId", "authorization", "body", "bodySize")
    }
}
