plugins {
    // NOTE: The plugin{} blocks is not normal, it can't run arbitrary code or access outer scope
    // This allows Gradle to treat the plugin block as side-effect free.
    // (I.e. don't try to define version numbers as ext constants, they won't be visible in here)
    id("com.android.library") version "8.0.1" apply false
    id("org.jetbrains.kotlin.android") version "1.8.21" apply false
    id("org.jetbrains.dokka") version "1.8.10" apply false
    id("digital.wup.android-maven-publish") version "3.6.2" apply false
    id("com.getkeepsafe.dexcount") version "4.0.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
