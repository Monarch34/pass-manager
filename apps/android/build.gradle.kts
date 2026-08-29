plugins {
    // AGP 9 has built-in Kotlin support, so there is no `kotlin-android` plugin to apply.
    // This module is deliberately NOT a Kotlin Multiplatform module: from AGP 9.0 the two
    // plugins are mutually exclusive, and an application entry point has no reason to be
    // multiplatform anyway — everything shared lives under core/.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    buildFeatures {
        compose = true
    }

    compileOptions {
        val java = JavaVersion.toVersion(libs.versions.jvmToolchain.get())
        sourceCompatibility = java
        targetCompatibility = java
    }
}

dependencies {
    // Only :core:vault is used directly; the rest come through it as `api` dependencies.
    // They are named anyway so that a module this application compiles against is visible
    // here rather than arriving invisibly through somebody else's transitive graph.
    implementation(project(":core:crypto"))
    implementation(project(":core:domain"))
    implementation(project(":core:format"))
    implementation(project(":core:vault"))

    // One bill of materials fixes every Compose artifact, so they cannot drift into a
    // combination nobody has tested together.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    // Brings androidx.fragment with it, which is why MainActivity is a FragmentActivity:
    // BiometricPrompt needs one and ComponentActivity is not.
    implementation(libs.androidx.biometric)
    // Ahead of what biometric 1.1.0 would otherwise pull in. See the catalog: the old
    // FragmentActivity crashes on any Activity Result launch.
    implementation(libs.androidx.fragment)
}
