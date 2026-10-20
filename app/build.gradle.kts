import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Base64
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
 * Release signing, from a directory named by the `rawkoontv.signingDir` Gradle property (local)
 * holding release.jks and signing.env, else from the CI secrets (SIGNING_KEY is the base64
 * keystore, written next to the build outputs). The property wins so a stray SIGNING_KEY in a
 * shell can't swap the key of a local release. Without either, release builds fall back to the
 * debug key so a fresh checkout still builds; the release workflow refuses to publish such a build.
 */
val signing: Map<String, String> = run {
    val fromDir = providers.gradleProperty("rawkoontv.signingDir").orNull?.let { dir ->
        val props = Properties().apply { File(dir, "signing.env").inputStream().use { load(it) } }
        mapOf(
            "store" to File(dir, "release.jks").path,
            "storePassword" to props.getProperty("KEY_STORE_PASSWORD").orEmpty().trim('"'),
            "alias" to props.getProperty("KEY_ALIAS").orEmpty().trim('"'),
            "keyPassword" to props.getProperty("KEY_PASSWORD").orEmpty().trim('"'),
        )
    }
    // The keystore is only written when a release is being built, not on every configuration.
    val releasing = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
    fromDir ?: System.getenv("SIGNING_KEY")?.takeIf { releasing && it.isNotBlank() }?.let { encoded ->
        val keystore = layout.buildDirectory.file("ci.keystore").get().asFile
        keystore.parentFile.mkdirs()
        keystore.writeBytes(Base64.getMimeDecoder().decode(encoded))
        mapOf(
            "store" to keystore.path,
            "storePassword" to System.getenv("KEY_STORE_PASSWORD").orEmpty(),
            "alias" to System.getenv("KEY_ALIAS").orEmpty(),
            "keyPassword" to System.getenv("KEY_PASSWORD").orEmpty(),
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
        manifestPlaceholders["appLabel"] = "@string/app_name"
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
            manifestPlaceholders["appLabel"] = "Rawkoon TV · next"
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
            manifestPlaceholders["appLabel"] = "Rawkoon TV · next"
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

    // The ffmpeg decoders are native: one APK per ABI keeps each download small, plus a universal one.
    // The names match what the first RawkoonTV's updater looks for.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
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

    // The player: media3 at the version the Jellyfin ffmpeg decoders are built against
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

/**
 * Names the APKs for the updaters: RawkoonTV-<abi>.apk per ABI and RawkoonTV.apk for the universal
 * one, the names the first RawkoonTV's updater looks for and this one's too. Preview and debug
 * builds carry their build type so they never pass for a release.
 */
androidComponents {
    onVariants(selector().all()) { variant ->
        val prefix = if (variant.buildType == "release") "RawkoonTV" else "RawkoonTV-${variant.buildType}"
        variant.outputs.map { it as VariantOutputImpl }.forEach { output ->
            val abi = output.getFilter(FilterConfiguration.FilterType.ABI)?.let { "-${it.identifier}" }.orEmpty()
            output.outputFileName = "$prefix$abi.apk"
        }
    }
}
