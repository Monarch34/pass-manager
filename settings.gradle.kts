pluginManagement {
    repositories {
        // Narrowed by group so an Android artifact can only ever come from Google's
        // repository. Without the filter, `google()` being listed first means a
        // compromised or typosquatted coordinate on any other host is reachable for
        // everything the build resolves.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // A module that declares its own repositories is a module whose dependencies can come
    // from somewhere this file never approved.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "passmanager"

// ── The shared core ─────────────────────────────────────────────────────────
// Split by what each part is allowed to know, not by size. The dependency arrows only
// ever point downward:
//
//   apps:android ─┬─> core:vault ──┐
//                 ├─> core:format ─┼─> core:crypto   (primitives; knows nothing else)
//                 └─> core:domain ─┘   (the item model; a leaf)
//
// crypto knows nothing about items and format knows nothing about storage, so neither can
// grow a dependency on the other by accident.
include(":core:crypto")
include(":core:domain")
include(":core:format")
include(":core:vault")

// There is no :protocol module. There was one — a build script, no source, no tests,
// configuring four Kotlin compilations that compiled nothing — reserved for the desktop
// pairing wire contract. The reasoning for giving that contract its own module is sound and
// unchanged: two applications have to agree on it byte for byte, and a contract living
// inside one of them is not a contract. It comes back with the first wire type that needs
// it, rather than standing in the dependency diagram claiming to exist.

// ── Applications ────────────────────────────────────────────────────────────
// The Android entry point must be its own module: from AGP 9.0 the Kotlin Multiplatform
// plugin and `com.android.application` cannot coexist in one Gradle project.
include(":apps:android")

// ── Tools ───────────────────────────────────────────────────────────────────
// Not part of either application and not on any path that reaches one. This carries a
// version 1 vault forward exactly once, on a desk; users of version 2 have never heard of
// version 1 and there is no import screen. Delete this line and the directory when it has
// done its job.
include(":tools:v1-import")

// The umbrella that becomes PassManagerKit.xcframework. It holds no code — it exists so
// that which modules Swift can see is one decision in one file, and so the core modules
// never learn that an Apple platform exists.
include(":ios:framework")
