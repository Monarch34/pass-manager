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

// Reads a mandatory keystore.properties entry. Names the offending key instead of failing with a
// bare NPE when the file was copied from keystore.properties.example but never filled in.
fun requireKeystoreProperty(key: String): String =
    keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: throw GradleException(
            "keystore.properties is missing a value for `$key`: " +
                "fill in every entry from keystore.properties.example before building a release."
        )

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
        // The `release` config only exists when keystore.properties is present. It is never
        // substituted by the debug config: the debug keystore is identical on every machine, so a
        // debug-signed "release" APK is both unpublishable and indistinguishable from a real build.
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = requireKeystoreProperty("keyAlias")
                keyPassword = requireKeystoreProperty("keyPassword")
                storeFile = rootProject.file(requireKeystoreProperty("storeFile"))
                storePassword = requireKeystoreProperty("storePassword")
            }
        }
    }

    buildTypes {
        // Production: R8 + resource shrink; signed with keystore.properties.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // null when keystore.properties is absent — the guard task below then fails the build
            // with an explanation instead of quietly emitting an APK signed with the debug key.
            signingConfig = signingConfigs.findByName("release")
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
            // MigrationTestHelper resolves schemas as "<dotted db class>/<version>.json" relative to
            // the assets root, which is exactly how KSP already lays out schemas/. Pointing the
            // assets root straight at it removes the copy step that used to rewrite the dotted
            // directory into package folders under an extra schemas/ prefix — a path Room never
            // looks in, which is why every migration test failed with FileNotFoundException.
            assets.srcDir(layout.projectDirectory.dir("schemas"))
        }
    }
}

// A release APK/AAB must never leave the machine unsigned or debug-signed. The check runs at task
// execution time (not during configuration) so `assembleDebug` and IDE sync keep working on
// machines that have no keystore.
//
// It is bound to the packaging tasks by exact name, for two reasons:
//   * `packageRelease` is what actually writes the artifact. Guarding the `assembleRelease`
//     lifecycle task instead would fail the build only after an unsigned APK had already been
//     written to build/outputs, which defeats the point.
//   * A `name.contains("Release")` filter also matches AGP internals such as
//     `bundleReleaseClassesToCompileJar` and `bundleReleaseResources`, which lint and unit tests
//     pull in — that would break tasks having nothing to do with shipping an artifact.
tasks.matching { task ->
    task.name == "packageRelease" || task.name == "packageReleaseBundle"
}.configureEach {
    val keystoreFile = keystorePropertiesFile
    doFirst {
        if (!keystoreFile.exists()) {
            throw GradleException(
                "keystore.properties not found: a release build cannot be signed. " +
                    "Copy keystore.properties.example to ${keystoreFile.path} and fill it in, " +
                    "or use assembleDebug for local testing."
            )
        }
    }
}

// ColorResourceTokenTest reads these XML files to check they still match the :protocol design
// tokens. Gradle does not treat resources as inputs to a JVM unit test task, so without this the
// test stays UP-TO-DATE after a colors.xml edit and the drift it exists to catch slips through.
tasks.withType<Test>().configureEach {
    inputs.files(
        layout.projectDirectory.file("src/main/res/values/colors.xml"),
        layout.projectDirectory.file("src/main/res/values-night/colors.xml")
    )
        .withPropertyName("themeColorResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .optional()
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
    implementation(libs.okhttp)

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
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
