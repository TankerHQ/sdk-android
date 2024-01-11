repositories {
    mavenCentral()
}

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "1.9.20"
}

dependencies {
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    implementation("com.github.pgreze:kotlin-process:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}