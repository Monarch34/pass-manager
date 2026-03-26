/**
 * Android application root: `include(":app")`.
 * Shared `protocol/` is an included composite build (pairing CBOR + DTOs).
 * Desktop: separate project in `desktop/` — see `desktop/settings.gradle.kts`.
 */
pluginManagement {
    repositories {
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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PassManager"
include(":app")

includeBuild("protocol") {
    dependencySubstitution {
        substitute(module("com.passmanager:passmanager-protocol")).using(project(":"))
    }
}
