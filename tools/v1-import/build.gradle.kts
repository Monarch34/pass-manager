plugins {
    // No version. The Kotlin plugin is already on the build's classpath because the shared
    // modules apply the multiplatform one, and asking for it again with a version is an
    // error rather than a no-op.
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

// A plain JVM module, not multiplatform, and deliberately so: this runs once, on a desk, on
// a file the person running it already has. There is nothing here for a phone to do.
//
// Nothing depends on this module. It is not on either application's path, it ships in
// nothing, and deleting it is one line in settings.gradle.kts plus this directory. It is
// built by CI only so that it does not rot before it is used.
kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(project(":core:crypto"))
    implementation(project(":core:domain"))
    implementation(project(":core:format"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

application {
    mainClass = "com.passmanager.tools.v1import.MainKt"
}
