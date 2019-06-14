package io.tanker.api

import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.tanker.bindings.TankerDeviceListFinalizer

/**
 * Information about one of the Tanker user's devices
 *
 * Please use the use the builder interface and the setter methods instead
 * of accessing the fields directly. Those are not part of the public API and
 * are subject to change.
 */
class TankerDeviceInfo(ptr: Pointer) : Structure(ptr) {
    @JvmField val deviceIdField: Pointer = ptr.getPointer(0)
    @JvmField val isRevokedField: Byte = ptr.getByte(Pointer.SIZE.toLong())
    var finalizer: TankerDeviceListFinalizer? = null

    fun getDeviceId(): String {
        return deviceIdField.getString(0)
    }

    fun isRevoked(): Boolean {
        return isRevokedField.toInt() != 0
    }

    override fun getFieldOrder(): List<String> {
        return listOf("deviceIdField", "isRevokedField")
    }
}
