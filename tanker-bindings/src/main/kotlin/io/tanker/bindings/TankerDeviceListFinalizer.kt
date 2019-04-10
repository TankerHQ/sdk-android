package io.tanker.bindings

import com.sun.jna.Pointer

class TankerDeviceListFinalizer(val tankerLib: TankerLib, val listPtr: Pointer) {
    protected fun finalize() {
        tankerLib.tanker_free_device_list(listPtr)
    }
}
