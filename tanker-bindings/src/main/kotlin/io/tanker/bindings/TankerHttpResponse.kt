package io.tanker.bindings

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.Structure


class TankerHttpResponse : Structure() {
    @JvmField var errorMsg: String? = null
    @JvmField var headersPtr: Pointer? = Pointer.NULL
    @JvmField var numHeaders: Int = 0
    @JvmField var body: Pointer? = null
    @JvmField var bodySize: Long = 0
    @JvmField var statusCode: Int = 0

    var headers: Array<TankerHttpHeader> = arrayOf()

    override fun write() {
        numHeaders = headers.size
        headersPtr = if (numHeaders == 0) {
            Pointer.NULL
        } else {
            val headerSize = headers[0].size().toLong()
            val mem = Memory(numHeaders * headerSize)
            for ((idx, header) in headers.withIndex()) {
                header.useMemory(mem.share(idx * headerSize))
                header.write()
            }
            mem
        }

        super.write()
    }

    override fun getFieldOrder(): List<String> {
        return listOf("errorMsg", "headersPtr", "numHeaders", "body", "bodySize", "statusCode")
    }
}
