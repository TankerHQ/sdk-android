package io.tanker.build

import com.github.pgreze.process.process
import com.github.pgreze.process.Redirect
import com.sun.jna.Platform
import java.io.File
import javax.inject.Inject
import kotlin.RuntimeException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import org.gradle.api.*
import org.gradle.api.tasks.*

@Serializable
data class ConanBuildInfo(
    val dependencies: List<ConanDepInfo>,
)

@Serializable
@Suppress("PropertyName")
data class ConanDepInfo(
    val name: String,
    val libs: List<String>,
    val lib_paths: List<String>,
)

object NativeHelper {
    private val kotlinJson = Json { ignoreUnknownKeys = true }
    val conanAndroidArchs = mapOf(
        "x86_64" to "x86_64", "x86" to "x86", "armeabi-v7a" to "armv7", "arm64-v8a" to "armv8"
    )

    fun getAndroidProfile(arch: String): String {
        return "android-${arch}"
    }

    fun getConanHostProfile(): String {
        val os = when (Platform.getOSType()) {
            Platform.MAC -> "macos"
            Platform.LINUX -> "linux"
            else -> throw RuntimeException("Unsupported host os ${Platform.getOSType()} for conan profile")
        }

        val arch = when (Platform.ARCH) {
            "x86-64" -> "x86_64"
            "aarch64" -> "armv8"
            else -> throw RuntimeException("Unsupported host arch ${Platform.ARCH} for conan profile")
        }

        return "${os}-${arch}-shared"
    }

    // This creates a .jar with our .so inside, so we can put it in the classpath for tests
    fun createNativeLibJar(prefixLibs: List<File>, installPath: File, jarName: String) {
        val prefixLibsPaths = prefixLibs.map { it.toString() }.toTypedArray()
        runBlocking {
            val res = process(
                "jar",
                "cf",
                jarName,
                "-C",
                installPath.toString(),
                *prefixLibsPaths,
                directory = installPath,
                stdout = Redirect.PRINT,
                stderr = Redirect.PRINT,
            )
            check(res.resultCode == 0)
        }
    }

    fun copyTankerHostLibs(sourceLibPath: File, libNames: List<String>, installLibPath: File) {
        val libFilePaths = libNames.map { File(sourceLibPath, System.mapLibraryName(it)) }
        copyTankerLibs(libFilePaths, installLibPath)
    }

    fun copyTankerAndroidLibs(sourceLibPath: File, libNames: List<String>, installLibPath: File) {
        val libFilePaths = libNames.map { File(sourceLibPath, "lib${it}.so") }
        copyTankerLibs(libFilePaths, installLibPath)
    }

    fun copyTankerLibs(libFilePaths: List<File>, installLibPath: File) {
        for (libFilePath in libFilePaths) {
            val prefixedLibPath = File(installLibPath, libFilePath.name)
            prefixedLibPath.parentFile.mkdirs()
            prefixedLibPath.writeBytes(libFilePath.readBytes())
        }
    }

    fun getTankerDepInfos(conanOutputPath: File): ConanDepInfo {
        val conanJsonFile = File(conanOutputPath, "conanbuildinfo.json")
        val conanInfo = kotlinJson.decodeFromString<ConanBuildInfo>(conanJsonFile.readText())
        return conanInfo.dependencies.find { it.name == "tanker" }
            ?: throw RuntimeException("Could not find tanker dep in conanbuildinfo.json")
    }
}

open class Builder() : DefaultTask() {
    private val projectPath = File(project.projectDir.absolutePath)

    @InputDirectory
    private val conanPath = File(projectPath, "conan")

    @OutputDirectories
    private val installPathBaseDebug = File(projectPath, "src/debug/jniLibs")

    @OutputDirectories
    private val installPathBaseRelease = File(projectPath, "src/release/jniLibs")

    init {
        // We need to mark the tanker library from the conan cache as Inputs of this task, but
        // the paths are computed in compileForAndroid().
        // For now we just always re-run this task, even if it should be up to date.
        outputs.upToDateWhen { false }
    }

    fun compileForAndroid() {
        // Run each known android arch
        NativeHelper.conanAndroidArchs.forEach { (k, v) ->
            val conanOutPath = File(conanPath, NativeHelper.getAndroidProfile(v))
            val tankerDepInfos = NativeHelper.getTankerDepInfos(conanOutPath)
            NativeHelper.copyTankerAndroidLibs(
                File(tankerDepInfos.lib_paths.first()),
                tankerDepInfos.libs,
                File(this.installPathBaseDebug, k)
            )
            NativeHelper.copyTankerAndroidLibs(
                File(tankerDepInfos.lib_paths.first()),
                tankerDepInfos.libs,
                File(this.installPathBaseRelease, k)
            )
        }
    }

    fun compileForHost() {
        val conanOutputPath = File(this.conanPath, NativeHelper.getConanHostProfile())
        val conanHostPath = File(this.conanPath, "host")
        conanHostPath.mkdirs()
        val tankerDepInfos = NativeHelper.getTankerDepInfos(conanOutputPath)
        val libNames = tankerDepInfos.libs
        val hostLibInstallPath = File(conanHostPath, Platform.RESOURCE_PREFIX)
        NativeHelper.copyTankerHostLibs(
            File(tankerDepInfos.lib_paths.first()), libNames, hostLibInstallPath
        )
        NativeHelper.createNativeLibJar(
            libNames.map { File(Platform.RESOURCE_PREFIX, System.mapLibraryName(it)) },
            conanHostPath,
            "tanker-native.jar"
        )
    }
}

open class BuildNative @Inject constructor(private val buildFor: String) : Builder() {
    @TaskAction
    fun compile() {
        if (buildFor == "host" || buildFor == "all") this.compileForHost()
        if (buildFor == "android" || buildFor == "all") this.compileForAndroid()
    }
}