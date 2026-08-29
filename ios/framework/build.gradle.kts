import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

/**
 * The umbrella that becomes `PassManagerKit.xcframework`.
 *
 * It holds no code. It exists so that iOS packaging decisions — which modules Swift can
 * see, static or dynamic linkage, the framework's name — live in one file instead of being
 * spread across every core module, and so that the core modules stay unaware that an Apple
 * platform exists at all.
 *
 * Nothing here can be built on a Windows or Linux host: linking an Apple binary needs
 * Xcode. Configuration succeeds everywhere, and `assemblePassManagerKitXCFramework` is
 * verified on macOS CI.
 */
kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    val frameworkName = "PassManagerKit"
    val xcf = XCFramework(frameworkName)

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = frameworkName

            // Static, for one concrete reason: a dynamic Kotlin framework embeds its own
            // static dependencies, so anything the Swift app links directly and this
            // framework also links appears twice and fails at link or load time. Static
            // also lets the linker drop what Swift never calls.
            //
            // The cost is real and worth stating: SwiftUI Previews are unreliable against
            // a static framework, and only one Kotlin framework in an app may be static.
            // One is all this project will ever ship.
            isStatic = true

            // Enumerated, never `transitiveExport = true`. Each module named here is one
            // Swift can see types from; anything absent stays an internal detail of the
            // Kotlin side. Transitive export would drag every dependency into the public
            // surface and into the binary, which is how a framework quietly doubles in
            // size and starts exposing types nobody meant to publish.
            //
            // A module can only be exported if it is an `api` dependency below.
            // `core:crypto` is exported, which reverses an earlier decision and is worth
            // saying why. The intent was that Swift should never touch raw cryptography.
            // But `PmVault.create` and `openWithPassphrase` take a `Secret`, so the type is
            // already in the surface Swift must call; leaving it unexported would not hide
            // it, only make it opaque — Swift would hold something it could neither
            // construct nor read. Exporting it is the honest version of the same boundary:
            // the app builds a Secret from the passphrase field and hands it straight back,
            // and no primitive is reachable from there.
            export(project(":core:crypto"))
            export(project(":core:domain"))
            export(project(":core:format"))
            export(project(":core:vault"))

            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:crypto"))
            api(project(":core:domain"))
            api(project(":core:format"))
            api(project(":core:vault"))
        }
    }
}
