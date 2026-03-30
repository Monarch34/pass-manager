// Android application module (`:app`). Root project: ../settings.gradle.kts
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.passmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.passmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")!!
                keyPassword = keystoreProperties.getProperty("keyPassword")!!
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile")!!)
                storePassword = keystoreProperties.getProperty("storePassword")!!
            }
        }
    }

    buildTypes {
        // Production: R8 + resource shrink; sign with keystore.properties when present (else debug keystore — not for store upload).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        // Development: faster iteration, no minify, debug signing.
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // BouncyCastle + jspecify (transitive) both ship this path on newer JDK metadata jars
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        // AGP 8.5+: uncompressed JNI + 16 KB zip alignment for Play / 16 KB page-size devices
        jniLibs {
            useLegacyPackaging = false
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir(layout.buildDirectory.dir("generated/roomAndroidTestAssets"))
        }
    }
}

// MigrationTestHelper expects assets under schemas/<pkg path>/; KSP exports to schemas/<single dotted dir>/
val copyRoomTestSchemas by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("schemas/com.passmanager.data.db.VaultDatabase"))
    into(
        layout.buildDirectory.dir(
            "generated/roomAndroidTestAssets/schemas/com/passmanager/data/db/VaultDatabase"
        )
    )
    include("*.json")
}

tasks.configureEach {
    if (name.startsWith("merge") && name.contains("AndroidTest", ignoreCase = true) &&
        name.contains("Assets", ignoreCase = true)
    ) {
        dependsOn(copyRoomTestSchemas)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("com.passmanager:passmanager-protocol:1.0.0")

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Full Material icon font (larger APK). To shrink: replace usages with material-icons-core glyphs only.
    implementation(libs.androidx.material.icons.extended)

    // Activity + Navigation
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Biometric
    implementation(libs.androidx.biometric)

    // Argon2
    implementation(libs.argon2kt)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Image loading (favicons)
    implementation(libs.coil.compose)

    // Ktor client (connects to desktop pairing server over LAN)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.slf4j.nop)

    // CameraX + ML Kit (QR code scanning for desktop pairing)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // BouncyCastle (X25519 ECDH on API < 33)
    implementation(libs.bouncycastle.provider)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
