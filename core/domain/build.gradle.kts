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
        namespace = "com.passmanager.domain"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        // The AGP Kotlin Multiplatform plugin creates no runnable test component unless
        // asked. Without this, commonTest compiles for Android and is never executed, so
        // the Android actual of every expect declaration goes untested while the JVM one
        // passes and the aggregate report looks complete.
        withHostTestBuilder {}.configure {}
    }

    jvm()

    // Three Apple targets, not two. An earlier version of this file left iosX64 out
    // because "every macOS runner in this project's CI is arm64" — which was true and
    // beside the point. `xcodebuild -destination 'generic/platform=iOS Simulator'` builds
    // both simulator architectures, and hosted simulator services are not all Apple
    // Silicon, so an arm64-only framework fails to link for anyone who is not on the
    // machine that built it. The slice is cheap; discovering it is missing is not.
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: Secret and SecretText appear in this module's own
            // public types, so anything holding a VaultItem needs to see them. Note the
            // arrow still points downward — crypto knows nothing about items, and this is
            // the item model admitting that some of its fields are key material.
            api(project(":core:crypto"))
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
