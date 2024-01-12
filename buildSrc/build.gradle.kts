repositories {
    mavenCentral()
}

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "1.9.20"
}

dependencies {
    implementation(libs.jna.platform)
    implementation(libs.pgreze.kotlin.process)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}