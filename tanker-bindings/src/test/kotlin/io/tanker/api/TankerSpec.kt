package io.tanker.api

import io.kotlintest.Spec
import io.kotlintest.TestCaseConfig
import io.kotlintest.seconds
import io.kotlintest.specs.StringSpec

abstract class TankerSpec : StringSpec() {
    protected val options = TankerOptions()
    override val defaultTestCaseConfig = TestCaseConfig(timeout = 30.seconds)
    lateinit var tc: Trustchain


    override fun beforeSpec(spec: Spec) {
        tc = Trustchain()
        options.setTrustchainId(tc.id())
                .setTrustchainUrl(tc.url)
                .setWritablePath(createTmpDir().toString())
                .setSdkType("test")
        setupTestEnv()
    }

    override fun afterSpec(spec: Spec) {
        tc.delete()
    }
}