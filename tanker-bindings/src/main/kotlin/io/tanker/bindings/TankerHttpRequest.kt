package io.tanker.bindings

import com.sun.jna.Pointer
import com.sun.jna.Structure

class TankerHttpRequest : Structure() {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField var method: String? = null
    @JvmField var url: String? = null
    @JvmField var headersPtr: TankerHttpHeader.ByReference? = null
    @JvmField var numHeaders: Int = 0
    @JvmField var body: Pointer? = null
    @JvmField var bodySize: Int = 0

    private var headers: Array<TankerHttpHeader> = arrayOf()

    fun getHeaders(): Array<TankerHttpHeader> {
        return headers
    }

    override fun read() {
        super.read()

        headers = Array(numHeaders) { TankerHttpHeader() }
        @Suppress("UNCHECKED_CAST")
        headers = headersPtr?.toArray(headers) as Array<TankerHttpHeader>
    }

    override fun getFieldOrder(): List<String> {
        return listOf("method", "url", "headersPtr", "numHeaders", "body", "bodySize")
    }
}
