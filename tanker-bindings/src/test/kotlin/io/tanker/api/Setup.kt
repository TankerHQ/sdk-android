package io.tanker.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.tanker.admin.Admin
import io.tanker.bindings.TankerAppDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

data class ConfigData(val idToken: String, val url: String)

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
    if(path == null || path.isEmpty()) {
        throw FileNotFoundException("$key not defined or empty")
    }
    return path

}

fun getConfigFromFile(configName: String): ConfigData {
    val filePath = safeGetEnv("TANKER_CONFIG_FILEPATH")
    val json = File(filePath).readText(Charsets.UTF_8)
    val mapper = jacksonObjectMapper()
    val node = mapper.readTree(json)
    return mapper.treeToValue(node.get(configName), ConfigData::class.java)
}

fun getOIDCConfigFromFile(): ConfigOIDC {
    val filePath = safeGetEnv("TANKER_CONFIG_FILEPATH")
    val json = File(filePath).readText(Charsets.UTF_8)
    val mapper = jacksonObjectMapper()
    val node = mapper.readTree(json)
    return mapper.treeToValue(node.get("oidc").get("googleAuth"), ConfigOIDC::class.java)
}

fun getConfigFromEnv(): ConfigData {
    return ConfigData(safeGetEnv("TANKER_URL"), safeGetEnv("TANKER_TOKEN"))
}

class Config{
    companion object {
        var instance: ConfigData? = null
        var instanceOIDC: ConfigOIDC? = null

        fun getUrl(): String {
            if (instance == null)
                Config()
            return instance!!.url
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
        val configName = safeGetEnv("TANKER_CONFIG_NAME")
        instance = if (configName == "ci")
            getConfigFromEnv()
        else
            getConfigFromFile(configName)
        instanceOIDC = getOIDCConfigFromFile()
    }
}

class App {
    val admin = Admin(Config.getUrl(), Config.getIdToken())
    val url: String = Config.getUrl()
    private val descriptor: TankerAppDescriptor

    init {
        admin.connect().get()
        descriptor = admin.createApp("android-test").get()
    }

    fun createIdentity(userId: String = UUID.randomUUID().toString()): String {
        return Identity.createIdentity(
                descriptor.id!!,
                descriptor.privateKey!!,
                userId
        )
    }

    fun id(): String {
        return descriptor.id!!
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
