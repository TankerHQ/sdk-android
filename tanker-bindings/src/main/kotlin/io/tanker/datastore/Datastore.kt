package io.tanker.datastore

import android.content.ContentValues
import android.database.sqlite.*
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.tanker.datastore.Utils.reportErrors
import io.tanker.datastore.Utils.convertToBufferList
import io.tanker.datastore.Utils.toHexString
import io.tanker.bindings.TankerDatastoreCacheGetResult
import io.tanker.bindings.TankerDatastoreDeviceGetResult
import io.tanker.bindings.DatastoreLib
import java.util.concurrent.atomic.AtomicInteger

class Datastore private constructor(dataPath: String, cachePath: String) {
    companion object {
        private val lastIdx = AtomicInteger(0)
        private val instances = mutableMapOf<Int, Datastore>()

        fun openDatastore(dataPath: String, cachePath: String): Int {
            val current = lastIdx.incrementAndGet()
            instances[current] = Datastore(dataPath, cachePath)
            return current
        }

        fun getInstance(idx: Int) = instances[idx]

        fun deleteInstance(idx: Int) = instances.remove(idx)?.close()

        const val TABLE_DEVICE = "device"
        const val TABLE_CACHE = "cache"

        const val LatestDeviceVersion = 1
        const val LatestCacheVersion = 1

        private fun initDb(db: SQLiteDatabase) {
            // for revocation (when wiping db)
            db.rawQuery("PRAGMA secure_delete = ON", arrayOf()).close()
            // Check the open succeeded
            db.rawQuery("SELECT count(*) FROM sqlite_master", arrayOf()).close()

            // This does not actually take the lock, we need to trigger a write operation
            // on the database for it to be taken. That's why we create a table and run an
            // update on it.
            db.rawQuery("PRAGMA locking_mode = EXCLUSIVE", arrayOf()).close()
            db.execSQL("CREATE TABLE IF NOT EXISTS access (last_access INT NOT NULL)")
            // Yes, it actually (tries to) write, and takes the lock
            db.execSQL("UPDATE access SET last_access = 0")
        }

        internal class DatabaseTooRecentError(m: String) : Exception(m)
    }

    private val dbDevice: SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase("$dataPath-device.db", null)
    private val dbCache: SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase("$cachePath-cache.db", null)

    init {
        try {
            initDb(dbDevice)
            initDb(dbCache)

            when (dbDevice.version) {
                0 -> {
                    createDeviceTable()
                    dbDevice.version = LatestDeviceVersion
                }
                LatestDeviceVersion -> Unit
                else ->
                    throw DatabaseTooRecentError("device database version too recent, expected $LatestDeviceVersion, got ${dbDevice.version}")
            }

            when (dbCache.version) {
                0 -> {
                    createCacheTable()
                    dbCache.version = LatestCacheVersion
                }
                LatestCacheVersion -> Unit
                else ->
                    throw DatabaseTooRecentError("cache database version too recent, expected $LatestCacheVersion, got ${dbCache.version}")
            }
        } catch (e: Throwable) {
            dbDevice.close()
            dbCache.close()
            throw e
        }
    }

    private fun createDeviceTable() {
        dbDevice.execSQL(
            """
            CREATE TABLE $TABLE_DEVICE (
                id INTEGER PRIMARY KEY,
                deviceblob BLOB NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createCacheTable() {
        dbCache.execSQL(
            """
            CREATE TABLE $TABLE_CACHE (
                key BLOB PRIMARY KEY,
                value BLOB NOT NULL
            )
            """.trimIndent()
        )
    }

    fun close() {
        dbDevice.close()
        dbCache.close()
    }

    fun nuke() {
        dbDevice.delete(TABLE_DEVICE, null, null)
        dbCache.delete(TABLE_CACHE, null, null)
    }

    fun putDevice(str: ByteArray) {
        val cv = ContentValues()
        cv.put("id", 1)
        cv.put("deviceblob", str)
        dbDevice.insertWithOnConflict(TABLE_DEVICE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getDevice(): ByteArray? {
        return dbDevice.query(TABLE_DEVICE, arrayOf("deviceblob"), null, null, null, null, null)
            .use {
                if (it.moveToNext())
                    it.getBlob(0)
                else
                    null
            }
    }

    fun putCache(data: Map<ByteArray, ByteArray>, onConflict: Int) {
        dbCache.beginTransaction()
        try {
            val cv = ContentValues()
            for (row in data) {
                cv.put("key", row.key)
                cv.put("value", row.value)
                dbCache.insertWithOnConflict(TABLE_CACHE, null, cv, onConflict)
            }
            dbCache.setTransactionSuccessful()
        } finally {
            dbCache.endTransaction()
        }
    }

    // You can't just put a ByteArray in a Map, so here's a correct ByteArray
    private class ByteArrayKey(val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            this === other || other is ByteArrayKey && this.bytes contentEquals other.bytes

        override fun hashCode(): Int = bytes.contentHashCode()
        override fun toString(): String = bytes.contentToString()
    }

    fun getCache(keys: List<ByteArray>): List<ByteArray?> {
        if (keys.isEmpty())
            return listOf()

        val keysString = keys.joinToString(",") { "x'${toHexString(it)}'" }
        val map = mutableMapOf<ByteArrayKey, ByteArray>()
        dbCache.query(
            TABLE_CACHE,
            arrayOf("key", "value"),
            "key IN ($keysString)",
            null,
            null,
            null,
            "key"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                map[ByteArrayKey(cursor.getBlob(0))] = cursor.getBlob(1)
            }
        }
        return keys.map { map[ByteArrayKey(it)] }
    }
}

enum class DatastoreError(val value: Byte) {
    INVALID_DATABASE_VERSION(1),
    RECORD_NOT_FOUND(2),
    DATABASE_ERROR(3),
    DATABASE_LOCKED(4),
    DATABASE_CORRUPT(5),
    DATABASE_TOO_RECENT(6),
    CONSTRAINT_FAILED(7),
}

class DatastoreOpen(private val datastoreLib: DatastoreLib) :
    DatastoreLib.DatastoreOpenCallback {
    override fun callback(handle: Pointer, db: Pointer, data_path: String, cache_path: String) =
        reportErrors(datastoreLib, handle) {
            val idx = Datastore.openDatastore(data_path, cache_path)
            db.write(0, arrayOf<Pointer>(Pointer.createConstant(idx)), 0, 1)
        }
}

class DatastoreClose(private val datastoreLib: DatastoreLib) :
    DatastoreLib.DatastoreCloseCallback {
    override fun callback(datastore: Pointer) {
        Datastore.deleteInstance(Pointer.nativeValue(datastore).toInt())
    }
}

class DatastoreNuke(private val datastoreLib: DatastoreLib) :
    DatastoreLib.DatastoreNukeCallback {
    override fun callback(datastore: Pointer, handle: Pointer) {
        reportErrors(datastoreLib, handle) {
            val db = Datastore.getInstance(Pointer.nativeValue(datastore).toInt())!!
            db.nuke()
        }
    }
}

class DatastoreDevicePut(private val datastoreLib: DatastoreLib) :
    DatastoreLib.DatastoreDevicePutCallback {
    override fun callback(datastore: Pointer, handle: Pointer, device: Pointer, size: Int) =
        reportErrors(datastoreLib, handle) {
            val db = Datastore.getInstance(Pointer.nativeValue(datastore).toInt())!!
            val deviceData = ByteArray(size)
            device.read(0, deviceData, 0, size)
            db.putDevice(deviceData)
        }
}

class DatastoreDeviceGet(private val datastoreLib: DatastoreLib) :
    DatastoreLib.DatastoreDeviceGetCallback {
    override fun callback(datastore: Pointer, h: TankerDatastoreDeviceGetResult) =
        reportErrors(datastoreLib, h) {
            val db = Datastore.getInstance(Pointer.nativeValue(datastore).toInt())!!
            val deviceData = db.getDevice()
            if (deviceData == null)
                return@reportErrors
            val outPtr = datastoreLib.tanker_datastore_allocate_device_buffer(h, deviceData.size)
            outPtr.write(0, deviceData, 0, deviceData.size)
        }
}

class DatastoreCachePut(private val datastoreLib: DatastoreLib) :
    DatastoreLib.DatastoreCachePutCallback {
    override fun callback(
        datastore: Pointer,
        handle: Pointer,
        keys: Pointer?,
        key_sizes: Pointer?,
        values: Pointer?,
        value_sizes: Pointer?,
        elem_count: Int,
        onConflict: Byte
    ) = reportErrors(datastoreLib, handle) {
        if (elem_count == 0)
            return@reportErrors

        if (keys == null || key_sizes == null || values == null || value_sizes == null)
            throw java.lang.RuntimeException("invalid pointers with elem_count = $elem_count")

        val db = Datastore.getInstance(Pointer.nativeValue(datastore).toInt())!!
        val keyList = convertToBufferList(keys, key_sizes, elem_count)
        val valueList = convertToBufferList(values, value_sizes, elem_count)
        val keyValues = keyList.zip(valueList).toMap()

        val onConflictAndroid = when (onConflict) {
            0.toByte() -> SQLiteDatabase.CONFLICT_FAIL
            1.toByte() -> SQLiteDatabase.CONFLICT_IGNORE
            2.toByte() -> SQLiteDatabase.CONFLICT_REPLACE
            else -> throw RuntimeException("invalid conflict algorithm: $onConflict")
        }

        db.putCache(keyValues, onConflictAndroid)
    }
}

class DatastoreCacheGet(private val datastoreLib: DatastoreLib) :
    DatastoreLib.DatastoreCacheGetCallback {
    override fun callback(
        datastore: Pointer,
        h: TankerDatastoreCacheGetResult,
        keys: Pointer?,
        key_sizes: Pointer?,
        elem_count: Int
    ) = reportErrors(datastoreLib, h) {
        if (elem_count == 0)
            return@reportErrors

        if (keys == null || key_sizes == null)
            throw java.lang.RuntimeException("invalid pointers with elem_count = $elem_count")

        val db = Datastore.getInstance(Pointer.nativeValue(datastore).toInt())!!
        val keyList = convertToBufferList(keys, key_sizes, elem_count)

        val valueList = db.getCache(keyList)

        val valueSizes = Memory((valueList.size * 4).toLong())
        valueSizes.write(0, valueList.map { it?.size ?: -1 }.toIntArray(), 0, valueList.size)
        val valueBuffers = Memory((Native.POINTER_SIZE * valueList.size).toLong())
        datastoreLib.tanker_datastore_allocate_cache_buffer(
            h,
            valueBuffers.share(0),
            valueSizes.share(0)
        )

        for ((idx, value) in valueList.withIndex())
            if (value != null) {
                valueBuffers.getPointer((idx * Native.POINTER_SIZE).toLong())
                    .write(0, value, 0, value.size)
            }
    }
}
