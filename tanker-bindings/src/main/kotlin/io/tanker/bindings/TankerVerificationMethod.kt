package io.tanker.bindings

import com.sun.jna.Pointer
import com.sun.jna.Structure

class TankerVerificationMethod(ptr: Pointer) : Structure(ptr) {
    // NOTE: Remember to keep the version in sync w/ the c++!
    @JvmField
    var version: Byte = 2
    @JvmField
    var type: Byte = 0
    @JvmField
    var value1: String? = null
    @JvmField
    var value2: String? = null

    init {
        super.read()
    }

    override fun getFieldOrder(): List<String> {
        return listOf("version", "type", "value1", "value2")
    }
}
