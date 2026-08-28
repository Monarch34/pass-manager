// Plugin versions only. Nothing is applied here, and no `subprojects { }` or
// `allprojects { }` block configures anything from the root: cross-project configuration
// is what Gradle's configuration cache and isolated-projects work exist to remove, and a
// root file that reaches into every module is also the file nobody can read a single
// module's build out of.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
}
