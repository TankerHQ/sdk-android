plugins {
    // NOTE: The plugin{} blocks is not normal, it can't run arbitrary code or access outer scope
    // This allows Gradle to treat the plugin block as side-effect free.
    // (There is an exception for version catalogs, which works without warnings on Gradle >= 8.1)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.wup.android.maven.publish) apply false
    alias(libs.plugins.getkeepsafe.dexcount) apply false
}
