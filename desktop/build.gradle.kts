// Compose Desktop + Ktor server — standalone project (see ../docs/REPOSITORY_LAYOUT.md)
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

group = "com.passmanager.desktop"
version = "1.0.0"

dependencies {
    implementation("com.passmanager:passmanager-protocol:1.0.0")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.bouncycastle.provider)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))

    // Server-layer tests drive a real PairingServer over loopback: start() builds its own
    // embeddedServer, so the routes cannot be reached through Ktor's testApplication harness
    // without duplicating the routing block. That means the tests need a Ktor CLIENT, not the
    // server test host. Coordinates are built from the catalog's `ktor` version rather than
    // hard-coded so they cannot drift from the server artifacts above.
    val ktorVersion = libs.versions.ktor.get()
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-websockets:$ktorVersion")
}

compose.desktop {
    application {
        mainClass = "com.passmanager.desktop.MainKt"

        // Development: `./gradlew run`. Production Windows installer: `./gradlew packageMsi` (full JDK + WiX on build machine — see docs/BUILD.md).
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "PassManager Desktop"
            packageVersion = project.version.toString()

            windows {
                menuGroup = "PassManager"
                upgradeUuid = "d4e7f8a1-2b3c-4d5e-6f7a-8b9c0d1e2f3a"

                // Installer, Start menu and shortcut icon. The .ico is a binary asset that is
                // generated outside the build; when it is missing, jpackage falls back to its
                // own default icon rather than failing configuration.
                val windowsIcon = project.file("src/main/resources/app-icon.ico")
                if (windowsIcon.exists()) {
                    iconFile.set(windowsIcon)
                }
            }

            // Deb is a declared target format, so the .png is a packaging input rather than dead
            // weight in the jar. macOS is left on the jpackage default: .icns cannot be produced
            // here, and Compose Desktop only builds the host OS format anyway.
            linux {
                val linuxIcon = project.file("src/main/resources/app-icon.png")
                if (linuxIcon.exists()) {
                    iconFile.set(linuxIcon)
                }
            }
        }
    }
}
