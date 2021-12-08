package io.tanker.api

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.Timeout

abstract class TankerSpec {
    companion object {
        @JvmStatic
        protected val options = TankerOptions()
        @JvmStatic
        lateinit var tc: App

        @BeforeClass
        @JvmStatic
        fun beforeSpec() {
            tc = App()
            options.setTrustchainId(tc.id())
                    .setUrl(tc.url)
                    .setPersistentPath(createTmpDir().toString())
                    .setCachePath(createTmpDir().toString())
                    .setSdkType("test")
            setupTestEnv()
        }

        @AfterClass
        @JvmStatic
        fun afterSpec() {
            tc.delete()
        }
    }

    @Rule
    @JvmField
    val timeout: Timeout = Timeout.seconds(10)
}
