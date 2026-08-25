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

// The packaging rasters are rendered from the LogoPalette tokens at build time instead of being
// committed as binaries. Checked-in .png/.ico files were the only copies of the mark that could not
// follow a palette or coordinate edit, which is how the installer and Start-menu icon ended up
// showing older art than the running app.
val appIconsDir = layout.buildDirectory.dir("generated/app-icons")
val generateAppIcons = tasks.register<JavaExec>("generateAppIcons") {
    group = "build"
    description = "Renders app-icon.png and app-icon.ico from the LogoPalette tokens."
    // project.the<>(): inside a task-configuration block the implicit receiver is the task, which is
    // ExtensionAware in its own right and carries no SourceSetContainer.
    classpath = project.the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("com.passmanager.desktop.tools.AppIconGeneratorKt")
    // The generator only rasterises through Java2D; a headless JVM keeps it usable on CI.
    systemProperty("java.awt.headless", "true")
    argumentProviders.add(CommandLineArgumentProvider { listOf(appIconsDir.get().asFile.absolutePath) })
    outputs.dir(appIconsDir)
}

// Design-review renderer: draws every screen with representative fake state into
// build/ui-previews/*.png, both themes, no window and no paired phone needed. The screens behind
// the pairing handshake (Verify, VaultBrowser) are otherwise a two-device ritual to even look at.
val renderUiPreviews = tasks.register<JavaExec>("renderUiPreviews") {
    group = "verification"
    description = "Renders each desktop screen to build/ui-previews as PNGs, in both themes."
    // project.the<>(): inside the task-configuration block the receiver is the task, which has
    // no SourceSetContainer of its own — same trap generateAppIcons already documents.
    classpath = project.the<SourceSetContainer>()["test"].runtimeClasspath
    mainClass.set("com.passmanager.desktop.tools.UiPreviewRendererKt")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(layout.buildDirectory.dir("ui-previews").get().asFile.absolutePath)
    })
    outputs.dir(layout.buildDirectory.dir("ui-previews"))
}

compose.desktop {
    application {
        mainClass = "com.passmanager.desktop.MainKt"

        // Development: `./gradlew run`. Production Windows installer: `./gradlew packageMsi` (full JDK + WiX on build machine — see docs/BUILD.md).
        nativeDistributions {
            // macOS is deliberately absent: there is no `macOS { }` block here and no .icns can be
            // produced in this repo, so declaring Dmg would ship an artifact carrying jpackage's
            // default Java icon and no bundle identifier. Re-adding it means adding an .icns plus a
            // `macOS { iconFile; bundleID }` block in the same change.
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "PassManager Desktop"
            packageVersion = project.version.toString()

            windows {
                // Both default to false, so menuGroup alone was inert: jpackage produced neither a
                // Start-menu entry nor a desktop shortcut, which are the two places the .ico below
                // is meant to appear.
                menu = true
                shortcut = true
                menuGroup = "PassManager"
                upgradeUuid = "d4e7f8a1-2b3c-4d5e-6f7a-8b9c0d1e2f3a"

                // Installer, Start menu and shortcut icon, rendered by generateAppIcons. Mapping the
                // TaskProvider carries the dependency, so packaging runs the generator first.
                iconFile.set(generateAppIcons.map { appIconsDir.get().file("app-icon.ico") })
            }

            linux {
                // Debian package names must be lowercase and space-free. Without this the name is
                // sanitised out of "PassManager Desktop" by the bundler rather than chosen here,
                // alongside the menuGroup and upgradeUuid this file already pins.
                packageName = "passmanager-desktop"
                iconFile.set(generateAppIcons.map { appIconsDir.get().file("app-icon.png") })
            }
        }
    }
}
