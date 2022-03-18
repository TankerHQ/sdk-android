package io.tanker.api

import android.system.Os.setenv
import androidx.test.platform.app.InstrumentationRegistry
import io.tanker.admin.Admin
import io.tanker.admin.TankerApp
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

data class ConfigData(val appManagementToken: String, val appManagementUrl: String, val environmentName: String, val trustchaindUrl: String, val url: String)

data class ConfigOIDC(
        val clientId: String,
        val clientSecret: String,
        val provider: String,
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
        )
        instanceOIDC = ConfigOIDC(
                clientId = safeGetEnv("TANKER_OIDC_CLIENT_ID"),
                clientSecret = safeGetEnv("TANKER_OIDC_CLIENT_SECRET"),
                provider = safeGetEnv("TANKER_OIDC_PROVIDER"),
                users = mapOf(Pair("martine",
                        ConfigOIDCUser(
                                email = safeGetEnv("TANKER_OIDC_MARTINE_EMAIL"),
                                refreshToken = safeGetEnv("TANKER_OIDC_MARTINE_REFRESH_TOKEN")
                        )), Pair("kevin",
                        ConfigOIDCUser(
                                email = safeGetEnv("TANKER_OIDC_KEVIN_EMAIL"),
                                refreshToken = safeGetEnv("TANKER_OIDC_KEVIN_REFRESH_TOKEN")
                        ))
                )
        )
    }
}

class App {
    val admin = Admin(Config.getAppManagementUrl(), Config.getAppManagementToken(), Config.getTrustchaindUrl(), Config.getEnvironmentName())
    val url: String = Config.getUrl()
    private val app: TankerApp

    init {
        admin.connect().get()
        app = admin.createApp("android-test").get()
    }

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

    fun authToken(): String {
        return app.authToken
    }

    fun trustchaindUrl(): String {
        return Config.getTrustchaindUrl()
    }

    fun getEmailVerificationCode(email: String): String {
        return app.getEmailVerificationCode(email).get()
    }

    fun getSMSVerificationCode(phoneNumber: String): String {
        return app.getSMSVerificationCode(phoneNumber).get()
    }

    fun delete() {
        admin.deleteApp(id()).get()
    }
}

fun createTmpDir(): Path {
    val path = Files.createTempDirectory("tmp-tanker-tests")
    path.toFile().deleteOnExit()
    return path
}

fun setupTestEnv() {
    Tanker.setLogHandler(object : LogHandlerCallback {
        override fun callback(logRecord: LogRecord) {
            if (logRecord.level == TankerLogLevel.DEBUG.value)
                return
            println("${logRecord.category}: ${logRecord.message}")
        }
    })
}
