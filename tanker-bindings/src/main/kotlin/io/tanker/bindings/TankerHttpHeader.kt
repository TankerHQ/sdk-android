package io.tanker.bindings

import com.sun.jna.Pointer
import com.sun.jna.Structure

open class TankerHttpHeader(@JvmField var name: String, @JvmField var value: String) : Structure() {
    constructor() : this("", "") {}
    class ByReference : TankerHttpHeader(), Structure.ByReference

    public override fun useMemory(m: Pointer?) {
        super.useMemory(m)
    }
    override fun getFieldOrder(): List<String> {
        return listOf("name", "value")
    }
}
