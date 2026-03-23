/**
 * PassManager — **Android** Gradle root only.
 *
 * Declares plugin versions for the `:app` module; does not apply `com.android.application` here.
 * The desktop app is a separate project under `/desktop` (see docs/REPOSITORY_LAYOUT.md).
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
