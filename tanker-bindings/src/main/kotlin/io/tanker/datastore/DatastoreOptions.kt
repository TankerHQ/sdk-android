package io.tanker.datastore

import com.sun.jna.Structure
import io.tanker.bindings.DatastoreLib

class DatastoreOptions() : Structure() {
    @JvmField var open: DatastoreLib.DatastoreOpenCallback? = null
    @JvmField var close: DatastoreLib.DatastoreCloseCallback? = null
    @JvmField var nuke: DatastoreLib.DatastoreNukeCallback? = null
    @JvmField var devicePut: DatastoreLib.DatastoreDevicePutCallback? = null
    @JvmField var deviceGet: DatastoreLib.DatastoreDeviceGetCallback? = null
    @JvmField var cachePut: DatastoreLib.DatastoreCachePutCallback? = null
    @JvmField var cacheGet: DatastoreLib.DatastoreCacheGetCallback? = null

    constructor(lib: DatastoreLib) : this() {
        open = DatastoreOpen(lib)
        close = DatastoreClose(lib)
        nuke = DatastoreNuke(lib)
        deviceGet = DatastoreDeviceGet(lib)
        devicePut = DatastoreDevicePut(lib)
        cacheGet = DatastoreCacheGet(lib)
        cachePut = DatastoreCachePut(lib)
    }

    override fun getFieldOrder() =
        listOf(
            "open",
            "close",
            "nuke",
            "devicePut",
            "deviceGet",
            "cachePut",
            "cacheGet",
        )
}

