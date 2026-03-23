// PassManager desktop application (Compose Desktop). Not part of the Android Gradle project
// in the parent folder; clone/build from repo root for Android, or from this folder for desktop.
pluginManagement {
    repositories {
        google()
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

rootProject.name = "passmanager-desktop"
