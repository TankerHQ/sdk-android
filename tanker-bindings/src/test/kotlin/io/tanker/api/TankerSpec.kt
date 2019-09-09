package io.tanker.api

import io.kotlintest.Spec
import io.kotlintest.TestCaseConfig
import io.kotlintest.seconds
import io.kotlintest.specs.StringSpec

abstract class TankerSpec : StringSpec() {
    protected val options = TankerOptions()
    override val defaultTestCaseConfig = TestCaseConfig(timeout = 30.seconds)
    lateinit var tc: App


    override fun beforeSpec(spec: Spec) {
        tc = App()
        options.setTrustchainId(tc.id())
                .setUrl(tc.url)
                .setWritablePath(createTmpDir().toString())
                .setSdkType("test")
        setupTestEnv()
    }

    override fun afterSpec(spec: Spec) {
        tc.delete()
    }
}