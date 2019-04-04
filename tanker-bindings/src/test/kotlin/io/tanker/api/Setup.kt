package io.tanker.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.tanker.bindings.TankerLib
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

data class ConfigData(val idToken: String, val url: String)

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

fun getConfigFromEnv(): ConfigData {
    return ConfigData(safeGetEnv("TANKER_URL"), safeGetEnv("TANKER_TOKEN"))
}

class Config{
    companion object {
        var instance: ConfigData? = null

        fun getTrustchainUrl(): String {
            if (instance == null)
                Config()
            return instance!!.url
        }

        fun getIdToken(): String {
            if (instance == null)
                Config()

            return instance!!.idToken
        }
    }

    init {
        val configName = safeGetEnv("TANKER_CONFIG_NAME")
        instance = if (configName == "ci")
            getConfigFromEnv()
        else
            getConfigFromFile(configName)
    }
}

class Trustchain {
    private val admin = TankerAdmin(Config.getTrustchainUrl(), Config.getIdToken())
    private val descriptor: TankerTrustchainDescriptor

    init {
        admin.connect().get()
        descriptor = admin.createTrustchain("android-test").get()
        println(descriptor)
    }

    fun generateIdentity(): String {
        val userId = UUID.randomUUID().toString()
        return Identity.generate(
                descriptor.id!!,
                descriptor.privateKey!!,
                userId
        )
    }

    fun id(): String {
        return descriptor.id!!
    }

    fun delete() {
        admin.deleteTrustchain(id()).get()
    }

    val url: String = Config.getTrustchainUrl()

}

fun createTmpDir(): Path {
    val path = Files.createTempDirectory("tmp-tanker-tests")
    path.toFile().deleteOnExit()
    return path
}

fun setupTestEnv() {
    Tanker.setLogHandler(object : TankerLib.LogHandlerCallback {
        override fun callback(logRecord: TankerLogRecord) {
            if (logRecord.level == TankerLogLevel.DEBUG.value)
                return
            println("$logRecord.category: $logRecord.message")
        }
    })
}
