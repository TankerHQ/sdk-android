package io.tanker.api

import io.tanker.admin.Admin
import io.tanker.admin.TankerApp
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

data class ConfigData(val idToken: String, val url: String, val adminUrl: String)

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

        fun getAdminUrl(): String {
            if (instance == null)
                Config()
            return instance!!.adminUrl
        }

        fun getIdToken(): String {
            if (instance == null)
                Config()

            return instance!!.idToken
        }

        fun getOIDCConfig(): ConfigOIDC {
            if (instanceOIDC == null)
                Config()

            return instanceOIDC!!
        }
    }

    init {
        instance = ConfigData(
                idToken = safeGetEnv("TANKER_ID_TOKEN"),
                url = safeGetEnv("TANKER_TRUSTCHAIND_URL"),
                adminUrl = safeGetEnv("TANKER_ADMIND_URL")
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
    val admin = Admin(Config.getAdminUrl(), Config.getIdToken(), Config.getUrl())
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

    fun getVerificationCode(email: String): String {
        return app.getVerificationCode(email).get()
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
