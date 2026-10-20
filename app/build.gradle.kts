import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/**
 * The version comes from the latest `vX.Y.Z` tag, so a release is just a tag. Builds between
 * tags keep the last tag's version; the code is derived from it so it always increases.
 */
val gitTag: String = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { "v3.0.0" }
val semver = gitTag.removePrefix("v").split('.', '-').take(3).map { it.toIntOrNull() ?: 0 }

/**
 * Release signing, from environment variables (CI) or a directory named by the
 * `rawkoontv.signingDir` Gradle property (local) holding release.jks and signing.env. Without
 * either, release builds fall back to the debug key so a fresh checkout still builds.
 */
val signing: Map<String, String> = run {
    val fromEnv = System.getenv("RAWKOONTV_KEYSTORE")?.let {
        mapOf(
            "store" to it,
            "storePassword" to System.getenv("KEY_STORE_PASSWORD").orEmpty(),
            "alias" to System.getenv("KEY_ALIAS").orEmpty(),
            "keyPassword" to System.getenv("KEY_PASSWORD").orEmpty(),
        )
    }
    fromEnv ?: providers.gradleProperty("rawkoontv.signingDir").orNull?.let { dir ->
        val props = Properties().apply { File(dir, "signing.env").inputStream().use { load(it) } }
        mapOf(
            "store" to File(dir, "release.jks").path,
            "storePassword" to props.getProperty("KEY_STORE_PASSWORD").orEmpty().trim('"'),
            "alias" to props.getProperty("KEY_ALIAS").orEmpty().trim('"'),
            "keyPassword" to props.getProperty("KEY_PASSWORD").orEmpty().trim('"'),
        )
    } ?: emptyMap()
}

android {
    namespace = "cloud.rawkoon.tv"
    compileSdk = 36

    defaultConfig {
        // Kept from the first RawkoonTV, so installed copies update in place.
        applicationId = "cloud.rawkoon.tv"
        minSdk = 26
        targetSdk = 36
        // Far above the first RawkoonTV's codes (one per tag), so the update always goes forward.
        versionCode = (semver[0] * 10_000 + semver[1] * 100 + semver[2]).coerceAtLeast(1)
        versionName = semver.joinToString(".")
    }

    signingConfigs {
        if (signing.isNotEmpty()) {
            create("release") {
                storeFile = file(signing.getValue("store"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("alias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release, so testing never touches the profiles in use.
            applicationIdSuffix = ".next"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // A release build (optimized, not debuggable) that installs next to the real app: for
        // trying a change on a TV and for measuring it
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".next"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jellyfin.sdk:jellyfin-core:1.7.1")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
