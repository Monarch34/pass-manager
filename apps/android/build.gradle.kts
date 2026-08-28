plugins {
    // AGP 9 has built-in Kotlin support, so there is no `kotlin-android` plugin to apply.
    // This module is deliberately NOT a Kotlin Multiplatform module: from AGP 9.0 the two
    // plugins are mutually exclusive, and an application entry point has no reason to be
    // multiplatform anyway — everything shared lives under core/.
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.passmanager"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.passmanager"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "2.0.0"
    }

    compileOptions {
        val java = JavaVersion.toVersion(libs.versions.jvmToolchain.get())
        sourceCompatibility = java
        targetCompatibility = java
    }
}

dependencies {
    implementation(project(":core:crypto"))
    implementation(project(":core:domain"))
    implementation(project(":core:format"))
    implementation(project(":core:vault"))
    implementation(project(":protocol"))
}
