package io.tanker.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.sun.jna.*
import io.tanker.bindings.DatastoreLib
import io.tanker.datastore.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

typealias AdminPointer = Pointer

@Suppress("FunctionName")
internal interface DatastoreTestsLib : DatastoreLib, Library {
    companion object {
        fun create(): DatastoreTestsLib {
            System.setProperty("jna.debug_load", "true")
            return Native.load("tankerdatastoretests", DatastoreTestsLib::class.java)
        }
    }

    fun tanker_run_datastore_test(datastore_options: DatastoreOptions, persistent_path: String, output_path: String): Int
}

class DatastoreTests : TankerSpec() {
    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @Test
    fun runs_datastore_tests() {
        val datastoreTests = DatastoreTestsLib.create()
        val datastoreOptions = DatastoreOptions(datastoreTests)

        // stdout goes to a black hole on Android. There's a secret sauce that redirects kotlin's
        // println() to logcat, but nothing for the real stdout fd. We circumvent that by outputing
        // the logs to a file and printing the file after the tests have run.
        val testOutputPath = Paths.get(tempFolder.root.path, "testoutput.txt")
        val ret = datastoreTests.tanker_run_datastore_test(datastoreOptions, tempFolder.root.toString(), testOutputPath.toString())
        println(String(Files.readAllBytes(testOutputPath)))
        assertThat(ret).withFailMessage("Datastore tests have failed, please read logcat's output").isEqualTo(0)
    }
}
