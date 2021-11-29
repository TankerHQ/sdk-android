package io.tanker.datastore

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import com.sun.jna.Pointer
import io.tanker.bindings.DatastoreLib

// These could be free functions, we don't need an object. However, while the code does compile with free functions, it does not run:
// java.lang.NoSuchMethodError: No static method reportErrors(Lio/tanker/bindings/DatastoreLib;Lcom/sun/jna/Pointer;Lkotlin/jvm/functions/Function0;)V in class Lio/tanker/api/DatastoreKt; or its super classes (declaration of 'io.tanker.api.DatastoreKt' appears in /data/app/~~aiLDTDGppz0vw8jd7L1e-g==/io.tanker.tanker_bindings.test-xLRfkQfv185_0WF-y9cf-g==/base.apk!classes2.dex)
// So I put them in an object, which is itself free, almost the same, except this form works. I'm too old for this.

internal object Utils {
    fun reportErrors(datastoreLib: DatastoreLib, handle: Pointer, cb: () -> Unit) =
        try {
            cb();
        } catch (e: Datastore.Companion.DatabaseTooRecentError) {
            datastoreLib.tanker_datastore_report_error(
                handle,
                DatastoreError.DATABASE_TOO_RECENT.value,
                e.toString()
            )
        } catch (e: SQLiteConstraintException) {
            datastoreLib.tanker_datastore_report_error(
                handle,
                DatastoreError.CONSTRAINT_FAILED.value,
                e.toString()
            )
        } catch (e: SQLiteDatabaseLockedException) {
            datastoreLib.tanker_datastore_report_error(
                handle,
                DatastoreError.DATABASE_LOCKED.value,
                "database is locked by another Tanker instance"
            )
        } catch (e: SQLiteDatabaseCorruptException) {
            datastoreLib.tanker_datastore_report_error(
                handle,
                DatastoreError.DATABASE_CORRUPT.value,
                e.toString()
            )
        } catch (e: Exception) {
            datastoreLib.tanker_datastore_report_error(
                handle,
                DatastoreError.DATABASE_ERROR.value,
                e.toString()
            )
        }

    fun toHexString(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // Convert (uint8_t** bufs, uint32_t* buf_size, uint32_t elem_count) to a simple List<ByteArray>
    fun convertToBufferList(
        bufs: Pointer,
        buf_sizes: Pointer,
        elem_count: Int
    ): List<ByteArray> {
        val bufPtrs = Array(elem_count) { Pointer.NULL }
        bufs.read(0, bufPtrs, 0, elem_count)
        val bufSizes = IntArray(elem_count)
        buf_sizes.read(0, bufSizes, 0, elem_count)
        return bufSizes.zip(bufPtrs) { size, str -> str.getByteArray(0, size) }
    }
}

