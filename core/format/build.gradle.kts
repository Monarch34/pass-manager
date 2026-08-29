plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    // From AGP 9.0 this replaces `androidTarget()` plus the `com.android.library`
    // plugin, which are no longer allowed in the same module as Kotlin Multiplatform.
    android {
        namespace = "com.passmanager.format"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        // The AGP Kotlin Multiplatform plugin creates no runnable test component unless
        // asked. Without this, commonTest compiles for Android and is never executed, so
        // the Android actual of every expect declaration goes untested while the JVM one
        // passes and the aggregate report looks complete.
        withHostTestBuilder {}.configure {}
    }

    jvm()

    // iosArm64 and iosSimulatorArm64 are Kotlin/Native tier 1. iosX64 is tier 3 and buys
    // nothing here: every macOS runner in this project's CI is arm64, so an Intel
    // simulator slice would be built and never executed.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // format speaks in items, so ItemPayload is part of its own surface.
            api(project(":core:domain"))
            // How the container is sealed is format's business alone.
            implementation(project(":core:crypto"))
            // `implementation`, not `api`. Nothing public here names a JSON type: the
            // preserved members are held behind an internal property. That keeps the
            // serialisation library out of the Swift framework's exported surface, where it
            // would show up as types Swift could neither construct nor read.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
