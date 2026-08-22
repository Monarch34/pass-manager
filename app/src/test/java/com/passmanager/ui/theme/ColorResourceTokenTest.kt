package com.passmanager.ui.theme

import com.passmanager.protocol.design.LogoPalette
import com.passmanager.protocol.design.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one place the palette is not compiled against [Palette]: `res/values/colors.xml`.
 *
 * XML cannot reference a Kotlin constant, so those colors are hand-copied from the shared tokens.
 * A drift is invisible at build time and only shows up as a color seam — between the window
 * background and the Compose content, or between the launcher icon and the in-app shield. This test
 * turns that into a build failure instead.
 */
class ColorResourceTokenTest {

    private val colorsXml = File("src/main/res/values/colors.xml")

    /** `<color name="x">#AARRGGBB</color>` pairs, keyed by name. */
    private fun declaredColors(): Map<String, Long> {
        val text = colorsXml.readText()
        return Regex("""<color\s+name="([^"]+)"\s*>\s*#([0-9A-Fa-f]{8})\s*</color>""")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2].toLong(16) }
    }

    @Test
    fun `colors xml is present and parsed`() {
        assertTrue(
            "colors.xml not found at ${colorsXml.absolutePath} — unit tests must run with the " +
                "app module as the working directory",
            colorsXml.isFile
        )
        assertTrue("no <color> entries parsed from colors.xml", declaredColors().isNotEmpty())
    }

    @Test
    fun `window and launcher colors match the shared palette`() {
        val colors = declaredColors()
        val expected = mapOf(
            "window_background_light" to Palette.LIGHT_BACKGROUND,
            "window_background_dark" to Palette.DARK_BACKGROUND,
            "ic_launcher_primary_container" to Palette.LIGHT_PRIMARY_CONTAINER,
        )
        expected.forEach { (name, token) ->
            assertEquals(
                "@color/$name drifted from its Palette token — update colors.xml to match",
                token.toString(16),
                colors[name]?.toString(16)
            )
        }
    }

    @Test
    fun `logo colors match the shared logo palette`() {
        val colors = declaredColors()
        val expected = mapOf(
            "logo_teal_dark" to LogoPalette.TEAL_DARK,
            "logo_teal_light" to LogoPalette.TEAL_LIGHT,
            "logo_inner_left" to LogoPalette.INNER_LEFT,
            "logo_inner_right" to LogoPalette.INNER_RIGHT,
            "logo_circuit_left" to LogoPalette.CIRCUIT_LEFT,
            "logo_circuit_right" to LogoPalette.CIRCUIT_RIGHT,
        )
        expected.forEach { (name, token) ->
            assertEquals(
                "@color/$name drifted from its LogoPalette token — the launcher icon and the " +
                    "desktop shield would no longer match",
                token.toString(16),
                colors[name]?.toString(16)
            )
        }
    }

    /**
     * The launcher icon must not vary with the night configuration: the launcher renders the
     * adaptive icon in its own context and the store listing icon is a single asset.
     */
    @Test
    fun `launcher colors have no values-night override`() {
        val night = File("src/main/res/values-night/colors.xml")
        if (!night.isFile) return
        val text = night.readText()
        listOf("ic_launcher_primary_container", "logo_teal_dark", "logo_teal_light").forEach { name ->
            assertTrue(
                "@color/$name is overridden in values-night/ — the launcher icon would differ " +
                    "between light and dark devices and disagree with the store listing",
                !text.contains("\"$name\"")
            )
        }
    }
}
