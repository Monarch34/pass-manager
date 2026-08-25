package com.passmanager.desktop.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.passmanager.desktop.ui.PairScreen
import com.passmanager.desktop.ui.VaultBrowserScreen
import com.passmanager.desktop.ui.VerifyScreen
import com.passmanager.desktop.ui.theme.PassManagerDesktopTheme
import com.passmanager.protocol.ItemSummary
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders each desktop screen to a PNG, in both themes, without opening a window.
 *
 * `VerifyScreen` and `VaultBrowserScreen` are only reachable behind a completed pairing
 * handshake, which makes design review of them a full two-device ritual. This renderer feeds
 * them representative fake state through the same public composables `Main.kt` calls —
 * nothing in `desktop/src/main` is modified or bypassed — and writes what they would show to
 * `desktop/build/ui-previews/`. Run it with the `renderUiPreviews` Gradle task.
 *
 * The 520x720 canvas is the launch size `Main.kt` requests, and 2x density matches a typical
 * 200% Windows scaling, so text renders at the weight reviewers actually see.
 */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: "build/ui-previews")
    outDir.mkdirs()

    val fakeItems = listOf(
        ItemSummary("1", "GitHub", "https://github.com", "login"),
        ItemSummary("2", "Personal Visa", "", "card"),
        ItemSummary("3", "Wi-Fi Note", "", "note"),
        ItemSummary("4", "Passport", "", "identity"),
        ItemSummary("5", "Demo Bank", "", "bank"),
        ItemSummary("6", "Wikipedia", "https://wikipedia.org", "login"),
    )

    for (dark in listOf(true, false)) {
        val themeName = if (dark) "dark" else "light"

        render(outDir, "pair-$themeName", dark) {
                PairScreen(
                    qrContent = "passmanager://pair?preview-only-payload",
                    lanIp = "192.168.1.20",
                    port = 48792,
                    isDarkTheme = dark,
                    onToggleTheme = {}
                )
        }

        render(outDir, "verify-$themeName", dark) {
                VerifyScreen(
                    attemptsRemaining = 3,
                    error = null,
                    safetyNumber = "3F9A1C7E",
                    isDarkTheme = dark,
                    onToggleTheme = {},
                    onCodeSubmitted = {},
                    onCancel = {}
                )
        }

        render(outDir, "browser-$themeName", dark) {
                VaultBrowserScreen(
                    items = fakeItems,
                    clipboardStatus = null,
                    onCopyPassword = {},
                    onDisconnect = {},
                    isDarkTheme = dark,
                    onToggleTheme = {},
                    useGoogleFavicons = false,
                    onFaviconSourceChange = {},
                    onRefreshVault = {}
                )
        }

        render(outDir, "browser-empty-$themeName", dark) {
                VaultBrowserScreen(
                    items = emptyList(),
                    clipboardStatus = null,
                    onCopyPassword = {},
                    onDisconnect = {},
                    isDarkTheme = dark,
                    onToggleTheme = {},
                    useGoogleFavicons = false,
                    onFaviconSourceChange = {},
                    onRefreshVault = {}
                )
        }
    }
    println("previews written to ${outDir.absolutePath}")
}

private fun render(outDir: File, name: String, dark: Boolean, content: @Composable () -> Unit) {
    ImageComposeScene(
        width = 1040,
        height = 1440,
        density = Density(2f)
    ).use { scene ->
        scene.setContent {
            PassManagerDesktopTheme(darkTheme = dark) {
                // The same background Surface Main.kt paints around AppContent. Without it the
                // scene renders on the harness default white and a dark preview lies about the
                // backdrop every real launch shows.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    content()
                }
            }
        }
        val image = scene.render(nanoTime = 1_000_000_000L)
        val png = image.encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG encode failed for $name")
        File(outDir, "$name.png").writeBytes(png.bytes)
    }
}
