package io.tanker.api

import io.tanker.bindings.TankerLib
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import com.fasterxml.jackson.module.kotlin.*
import java.io.File

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

    fun generateUserToken(userId: String): String {
        return UserToken.generate(
                descriptor.id!!,
                descriptor.privateKey!!,
                userId
        )
    }

    fun id(): String
    {
        return descriptor.id!!
    }
    val url: String = Config.getTrustchainUrl()

}

class TestTrustchain {
    companion object {
        var instance: Trustchain? = null

        fun get(): Trustchain {
            if(instance == null) {
                instance = Trustchain()
            }
            return instance!!
        }
    }

}


fun createTmpDir(): Path {
    val path = Files.createTempDirectory("tmp-tanker-tests")
    path.toFile().deleteOnExit()
    return path
}

fun setupTestEnv() {
    Tanker.setLogHandler(object : TankerLib.LogHandlerCallback {
        override fun callback(category: String, level: Byte, message: String) {
            if (level == 'D'.toByte())
                return
            println("$category: $message")
        }
    })
}
