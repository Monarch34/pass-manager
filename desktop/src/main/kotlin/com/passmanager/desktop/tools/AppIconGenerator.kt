package com.passmanager.desktop.tools

import com.passmanager.desktop.ui.components.renderAppIconAwt
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders the packaging icons (`app-icon.png`, `app-icon.ico`) from the same AWT code the running
 * app draws its mark with, so they cannot drift from it.
 *
 * Both files used to be checked-in binaries with no generator anywhere in the repo. That made them
 * the only copies of the mark that could not follow a `LogoPalette` edit, and it is exactly how the
 * installer icon ended up frozen as a plate-less shield while the Android launcher kept its mint
 * plate. Regenerating them at build time (see the `generateAppIcons` task) closes that channel: a
 * colour or coordinate change now reaches the installer, the Start-menu entry and the desktop
 * shortcut without anyone remembering to re-export anything.
 */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: error("usage: AppIconGenerator <outputDir>"))
    outDir.mkdirs()

    ImageIO.write(renderAppIconAwt(256), "png", File(outDir, "app-icon.png"))
    writeIco(File(outDir, "app-icon.ico"), intArrayOf(16, 24, 32, 48, 64, 128, 256))
}

/**
 * Writes a multi-size .ico whose entries are PNG payloads. Windows Vista and later read PNG-
 * compressed entries directly, so there is no need to emit the older BMP/DIB form (which would also
 * need a hand-built AND mask for the transparent corners of the rounded plate).
 */
private fun writeIco(target: File, sizes: IntArray) {
    val payloads = sizes.map { size ->
        ByteArrayOutputStream().also { ImageIO.write(renderIcoFrame(size), "png", it) }.toByteArray()
    }

    val out = ByteArrayOutputStream()
    // ICONDIR — everything in the container is little-endian.
    out.u16(0)              // reserved
    out.u16(1)              // resource type: 1 = icon
    out.u16(sizes.size)

    // ICONDIRENTRY per size, in the same order as the payloads that follow them.
    var offset = 6 + 16 * sizes.size
    sizes.forEachIndexed { index, size ->
        // 256 does not fit in a byte; the format spells it 0.
        val dimension = if (size >= 256) 0 else size
        out.write(dimension)    // width
        out.write(dimension)    // height
        out.write(0)            // palette entry count: 0 = truecolour
        out.write(0)            // reserved
        out.u16(1)              // colour planes
        out.u16(32)             // bits per pixel
        out.u32(payloads[index].size)
        out.u32(offset)
        offset += payloads[index].size
    }

    payloads.forEach { out.write(it) }
    target.writeBytes(out.toByteArray())
}

/**
 * Renders one .ico frame by drawing large and reducing, rather than asking the vector code to draw at
 * the final size directly.
 *
 * Windows picks the 16 px frame for the taskbar and the Explorer detail view — the two places the
 * icon is seen most. At that size `renderAppIconAwt` maps the art through `scale = 16 / 480`, so the
 * 4-unit circuit strokes land at 0.13 px and Java2D's antialiasing has nothing left to resolve: the
 * six-colour lockup flattens into a mint blob. Rendering at 4x and reducing bicubically keeps the
 * strokes as visible grey-teal texture instead. Above 64 px the direct render already has the
 * detail, so oversampling there would only cost time.
 */
private fun renderIcoFrame(size: Int): BufferedImage {
    if (size > 64) return renderAppIconAwt(size)

    val source = renderAppIconAwt(size * 4)
    val reduced = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = reduced.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(source, 0, 0, size, size, null)
    } finally {
        g.dispose()
    }
    return reduced
}

private fun ByteArrayOutputStream.u16(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}

private fun ByteArrayOutputStream.u32(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}
