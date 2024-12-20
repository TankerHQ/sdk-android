import io.tanker.build.BuildNative

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dokka)
    alias(libs.plugins.getkeepsafe.dexcount)
    alias(libs.plugins.wup.android.maven.publish)
    `maven-publish`
}

group = "io.tanker"
version = "dev"

val minSdkVersion = 23
val targetSdkVersion = 34

android {
    namespace = "io.tanker.tanker_bindings"

    ndkVersion = "26.2.11394342"

    compileSdk = targetSdkVersion
    defaultConfig {
        minSdk = minSdkVersion

        ndk {
            moduleName = "tanker-bindings-jni"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        testInstrumentationRunnerArguments += System.getenv().filter { (k, _) -> k.startsWith("TANKER_") }
        testInstrumentationRunnerArguments += (System.getProperties().map { (k, v) -> (k as String to v as String) })
                                                                     .filter { (k, _) -> k.startsWith("TANKER_") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        targetSdk = targetSdkVersion
    }

    testOptions {
        targetSdk = targetSdkVersion
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    publishing {
        singleVariant("release")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            multiDexEnabled = true
        }
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }

    // This is the only way to build instrumented tests in release mode
    // https://stackoverflow.com/a/51719271/1810193
    if (project.hasProperty("androidTestRelease")) {
        testBuildType = "release"
        buildTypes {
            release {
                multiDexEnabled = true
            }
        }
    } else {
        // We want to exclude our datastoretest library in production builds,
        // except when building for connected androidTests.
        // (Please consult a doctor if your internal screaming lasts more than 4h)
        buildTypes {
            debug {
                packaging.jniLibs {
                    keepDebugSymbols.add("**/*.so")
                    excludes.add("**/libtankerdatastoretests.so")
                }
            }
            release {
                packaging.jniLibs {
                    excludes.add("**/libtankerdatastoretests.so")
                }
            }
        }
    }

    sourceSets.getByName("main") {
        java.srcDir("src/main/kotlin")
        java.exclude("src/main/kotlin/io/tanker/admin/**")
    }

    sourceSets.getByName("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    sourceSets.getByName("debug") {
        jniLibs.srcDir("src/debug/jniLibs")
    }

    sourceSets.getByName("release") {
        jniLibs.srcDir("src/release/jniLibs")
    }
}

// Note that we don't control the conan build type (debug/release) at this point,
// since that part is done earlier in run-ci.py.
// So there is no distinction between debug/release here,
// we copy the same native lib (whether debug or release) for both Android debug/release variants
tasks.register<BuildNative>("buildNativeHost", "host")
tasks.register<BuildNative>("buildNativeAndroid", "android")
tasks.register<BuildNative>("buildNative", "all")
tasks.clean {
    delete("${projectDir}/conan")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        outputs.upToDateWhen {false}
        showStandardStreams = true
    }
    systemProperty("jna.library.path", projectDir.absolutePath + "/conan/host")
}

tasks.register<Jar>("sourcesJar") {
    from(android.sourceSets.getByName("main").java.srcDirs)
    archiveClassifier.set("sources")
}

tasks.register<Jar>("javaDocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

tasks.dokkaJavadoc.configure {
    dokkaSourceSets {
        named("main") {
            // Exclude JNA R class
            perPackageOption {
                matchingRegex.set("com\\.sun\\.jna")
                suppress.set(true)
            }

            // Exclude our R class
            perPackageOption {
                matchingRegex.set("io\\.tanker\\.tanker_bindings")
                suppress.set(true)
            }

            // Exclude low-level JNA bindings
            perPackageOption {
                matchingRegex.set("io\\.tanker\\.bindings")
                suppress.set(true)
            }

            perPackageOption {
                matchingRegex.set("io\\.tanker\\.jni")
                suppress.set(true)
            }

            perPackageOption {
                matchingRegex.set("io\\.tanker\\.admin")
                suppress.set(true)
            }

            // Exclude Identity api
            perPackageOption {
                matchingRegex.set("io\\.tanker\\.api\\.Identity")
                suppress.set(true)
            }

            // Exclude HttpClient api
            perPackageOption {
                matchingRegex.set("io\\.tanker\\.api\\.HttpClient")
                suppress.set(true)
            }
        }
    }
}

// We use afterEvaluate to give the Android Gradle plugin time to inject the Adhoc Component 'release'
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                // Instead of components.android, we want only the release aar, otherwise publish
                // fails with "multiple artifacts with the identical extension and classifier",
                // because the debug and release .aar have no "classifier" to distinguish them
                from(components["release"])

                // These have different classifiers (doc and source) so no conflict here
                artifact(tasks.getByName("javaDocJar"))
                artifact(tasks.getByName("sourcesJar"))
            }
        }

        repositories {
            maven {
                name = "maven.tanker.io"
                url = uri("gcs://maven.tanker.io")
            }
        }
    }
}


dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.jna) {
        artifact {
            type = "aar"
        }
    }
    implementation(libs.androidx.annotation)
    implementation(libs.okhttp)

    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.rules)
    // assertj 3.0 is not compatible with API level < 26
    androidTestImplementation(libs.assertj.core)

    androidTestImplementation(libs.jackson.module.kotlin)
    androidTestImplementation(libs.slf4j.nop)
}
