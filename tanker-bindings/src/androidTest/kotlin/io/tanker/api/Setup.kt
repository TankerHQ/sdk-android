package io.tanker.api

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.tanker.api.admin.Admin
import io.tanker.api.admin.TankerApp
import java.io.File
import java.io.FileNotFoundException
import java.util.*

data class ConfigData(
    val appManagementToken: String,
    val appManagementUrl: String,
    val environmentName: String,
    val trustchaindUrl: String,
    val url: String,
    val verificationApiToken: String
)

data class ConfigOIDC(
    val clientId: String,
    val clientSecret: String,
    val displayName: String,
    val issuer: String,
    val fakeOidcIssuerUrl: String,
    val users: Map<String, ConfigOIDCUser>
)

data class ConfigOIDCUser(
    val email: String,
    val refreshToken: String
)

fun safeGetEnv(key: String): String {
    val path = System.getProperty(key) ?: System.getenv(key)
    if (path == null || path.isEmpty()) {
        throw FileNotFoundException("$key not defined or empty")
    }
    return path

}

class Config {
    companion object {
        var instance: ConfigData? = null
        var instanceOIDC: ConfigOIDC? = null

        fun getUrl(): String {
            if (instance == null)
                Config()
            return instance!!.url
        }

        fun getTrustchaindUrl(): String {
            if (instance == null)
                Config()
            return instance!!.trustchaindUrl
        }

        fun getAppManagementToken(): String {
            if (instance == null)
                Config()
            return instance!!.appManagementToken
        }

        fun getAppManagementUrl(): String {
            if (instance == null)
                Config()

            return instance!!.appManagementUrl
        }

        fun getEnvironmentName(): String {
            if (instance == null)
                Config()

            return instance!!.environmentName
        }

        fun getOIDCConfig(): ConfigOIDC {
            if (instanceOIDC == null)
                Config()

            return instanceOIDC!!
        }

        fun getVerificationApiToken(): String {
            if (instance == null)
                Config()

            return instance!!.verificationApiToken
        }
    }

    init {
        try {
            val arguments = InstrumentationRegistry.getArguments()
            for (key in arguments.keySet())
                if (key.startsWith("TANKER_"))
                    System.setProperty(key, arguments.getString(key))
            // ignore these error, we're probably not running on android
        } catch (_: NoClassDefFoundError) {
        } catch (_: IllegalStateException) {
        }

        instance = ConfigData(
            appManagementToken = safeGetEnv("TANKER_MANAGEMENT_API_ACCESS_TOKEN"),
            appManagementUrl = safeGetEnv("TANKER_MANAGEMENT_API_URL"),
            environmentName = safeGetEnv("TANKER_MANAGEMENT_API_DEFAULT_ENVIRONMENT_NAME"),
            trustchaindUrl = safeGetEnv("TANKER_TRUSTCHAIND_URL"),
            url = safeGetEnv("TANKER_APPD_URL"),
            verificationApiToken = safeGetEnv("TANKER_VERIFICATION_API_TEST_TOKEN"),
        )
        instanceOIDC = ConfigOIDC(
            clientId = safeGetEnv("TANKER_OIDC_CLIENT_ID"),
            clientSecret = safeGetEnv("TANKER_OIDC_CLIENT_SECRET"),
            displayName = safeGetEnv("TANKER_OIDC_PROVIDER"),
            issuer = safeGetEnv("TANKER_OIDC_ISSUER"),
            fakeOidcIssuerUrl = safeGetEnv("TANKER_FAKE_OIDC_URL") + "/issuers/main",
            users = mapOf(
                Pair(
                    "martine",
                    ConfigOIDCUser(
                        email = safeGetEnv("TANKER_OIDC_MARTINE_EMAIL"),
                        refreshToken = safeGetEnv("TANKER_OIDC_MARTINE_REFRESH_TOKEN")
                    )
                ), Pair(
                    "kevin",
                    ConfigOIDCUser(
                        email = safeGetEnv("TANKER_OIDC_KEVIN_EMAIL"),
                        refreshToken = safeGetEnv("TANKER_OIDC_KEVIN_REFRESH_TOKEN")
                    )
                )
            )
        )
    }
}

class App {
    val admin = Admin(
        Config.getAppManagementUrl(),
        Config.getAppManagementToken(),
        Config.getTrustchaindUrl(),
        Config.getEnvironmentName(),
        Config.getVerificationApiToken()
    )
    val url: String = Config.getUrl()
    private val app: TankerApp = admin.createApp("sdk-android-tests")

    fun createIdentity(userId: String = UUID.randomUUID().toString()): String {
        return Identity.createIdentity(
            app.id,
            app.privateKey,
            userId
        )
    }

    fun id(): String {
        return app.id
    }

    fun trustchaindUrl(): String {
        return Config.getTrustchaindUrl()
    }

    fun verificationApiToken(): String {
        return Config.getVerificationApiToken()
    }

    fun getEmailVerificationCode(email: String) = app.getEmailVerificationCode(email)
    fun getSMSVerificationCode(phoneNumber: String) = app.getSMSVerificationCode(phoneNumber)
    fun delete() = admin.deleteApp(id())
}

fun createTmpDir(): String {
    val baseDir = System.getProperty("java.io.tmpdir")?.let { File(it) }
    var dir: File
    do {
        val suffix = System.currentTimeMillis().toString()
        val randNum = UUID.randomUUID().toString().substring(0,8)
        dir = File(baseDir, "tmp-tanker-tests-$suffix-$randNum")
    } while (dir.exists())
    dir.mkdir()
    dir.deleteOnExit()
    return dir.toString()
}

fun setupTestEnv() {
    Tanker.setLogHandler(object : LogHandlerCallback {
        override fun callback(logRecord: LogRecord) {
            if (logRecord.level == TankerLogLevel.DEBUG.value)
                return

            val tag = "io.tanker.tanker_bindings"
            val msg = "${logRecord.category}: ${logRecord.message}"

            when (logRecord.level) {
                TankerLogLevel.INFO.value -> Log.i(tag, msg)
                TankerLogLevel.WARNING.value -> Log.w(tag, msg)
                TankerLogLevel.ERROR.value -> Log.e(tag, msg)
                else -> Log.i(tag, msg)
            }
        }
    })
}
