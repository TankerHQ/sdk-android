package io.tanker.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.sun.jna.*
import io.tanker.bindings.DatastoreLib
import io.tanker.datastore.*

typealias AdminPointer = Pointer

@Suppress("FunctionName")
internal interface DatastoreTestsLib : DatastoreLib, Library {
    companion object {
        fun create(): DatastoreTestsLib {
            System.setProperty("jna.debug_load", "true")
            return Native.load("tankerdatastoretests", DatastoreTestsLib::class.java)
        }
    }

    fun tanker_run_datastore_test(datastore_options: DatastoreOptions, persistent_path: String): Int
}

class DatastoreTests : TankerSpec() {
    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @Test
    fun runs_datastore_tests() {
        val datastoreTests = DatastoreTestsLib.create()
        val datastoreOptions = DatastoreOptions(datastoreTests)

        val ret = datastoreTests.tanker_run_datastore_test(datastoreOptions, tempFolder.root.toString())
        assertThat(ret).withFailMessage("Datastore tests have failed, please read logcat's output").isEqualTo(0)
    }
}
