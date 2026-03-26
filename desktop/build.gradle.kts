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
            packageVersion = "1.0.0"

            windows {
                menuGroup = "PassManager"
                upgradeUuid = "d4e7f8a1-2b3c-4d5e-6f7a-8b9c0d1e2f3a"
            }
        }
    }
}
