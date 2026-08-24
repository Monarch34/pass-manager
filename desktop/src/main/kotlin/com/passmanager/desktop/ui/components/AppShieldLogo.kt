package com.passmanager.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.passmanager.desktop.ui.Strings
import com.passmanager.protocol.design.LogoPalette
import com.passmanager.protocol.design.Palette
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import kotlin.math.*

/**
 * The app's brand mark: the vault shield on a rounded mint plate.
 *
 * The plate is the fixed [Palette.LIGHT_PRIMARY_CONTAINER] in *both* themes — the same value
 * Android carries as `@color/ic_launcher_primary_container` — and deliberately not the running
 * scheme's `primaryContainer`. The shield art is fixed light-scheme colour, so a theme-following
 * plate put the `#21837D` half of the shield on `#0D9488` in dark: 1.22:1, effectively invisible.
 * Against the pinned `#CCFBF1` the same two teals measure 4.05:1 and 5.43:1.
 *
 * [SHIELD_FILL_FRACTION] is `50.25 / 72`: the shield's share of the height of the Android adaptive
 * icon's guaranteed-visible region. Every plated surface — this composable, the Android in-app
 * plate, the launcher icon and the generated desktop app icon — treats its own square as that
 * region, so all four are literally the same lockup rather than four similar ones.
 *
 * The AWT drawing below is the desktop's copy of the geometry, in the canonical `x 60..340`,
 * `y 135..470` coordinate space. A JVM renderer cannot read an AAPT-compiled VectorDrawable, so the
 * copy is unavoidable; it is coordinate-for-coordinate identical to
 * `app/src/main/res/drawable/ic_vault_shield.xml` and any change to one must be made in the other
 * in the same edit.
 */
@Composable
fun AppShieldLogo(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // Keyed on the *pixel* height of the art, not the dp size, so a 150%/200% display — or a drag
    // between monitors with different scaling — re-renders instead of upscaling a stale raster.
    val artHeightPx = with(density) { (size * SHIELD_FILL_FRACTION).roundToPx() }.coerceIn(8, 2048)
    val bitmap = remember(artHeightPx) { renderVaultShield(artHeightPx) }

    Surface(
        // Percent, not dp: the corner radius has to stay 25% of the edge at 32.dp and at 112.dp.
        // The old absolute 22.dp was 92% of maximum at PairScreen's 48.dp (a circle) and 34% at 64.dp.
        shape = RoundedCornerShape(25),
        color = Color(Palette.LIGHT_PRIMARY_CONTAINER),
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap = bitmap,
                contentDescription = Strings.LOGO_CONTENT_DESCRIPTION,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(SHIELD_FILL_FRACTION)
            )
        }
    }
}

/** Shield height as a fraction of the plate edge; the width follows from the 280:335 art aspect. */
internal const val SHIELD_FILL_FRACTION = 0.697917f

// Canonical art box: x 60..340, y 135..470. Only the transform differs between renderers.
private const val ART_W = 280f
private const val ART_H = 335f
private const val ART_CX = 200f
private const val ART_CY = 302.5f

/** Wraps an ARGB token from [LogoPalette] into AWT's color type (`true` = honor the alpha channel). */
private fun argb(token: Long) = java.awt.Color(token.toInt(), true)

/** Compose-typed shield art, for use inside the UI tree. */
internal fun renderVaultShield(heightPx: Int): ImageBitmap =
    renderVaultShieldAwt(heightPx).toComposeImageBitmap()

/**
 * The bare shield art, tightly cropped to the 280:335 box and with no plate — the plate is drawn by
 * whatever composes this in (see [AppShieldLogo]).
 */
internal fun renderVaultShieldAwt(heightPx: Int): BufferedImage {
    val h = heightPx.coerceAtLeast(1)
    val scale = h / ART_H
    val w = Math.round(ART_W * scale).coerceAtLeast(1)

    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g: Graphics2D = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g.translate(w / 2.0 - ART_CX * scale, h / 2.0 - ART_CY * scale)
    g.scale(scale.toDouble(), scale.toDouble())
    drawShieldArt(g)

    g.dispose()
    return image
}

/**
 * The plated square app icon: the same lockup the launcher shows, for the window/taskbar icon and
 * for the generated `app-icon.png` / `app-icon.ico`.
 *
 * `scale = s / 480f` is the whole framing rule: the art is 280x335 in a 480-unit square, so it
 * lands on `0.583333 x 0.697917` of the edge, centred. At `s = 256` that is x 53.333..202.667 and
 * y 38.667..217.333.
 */
internal fun renderAppIconAwt(sizePx: Int): BufferedImage {
    val s = sizePx.coerceAtLeast(8)

    val image = BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB)
    val g: Graphics2D = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g.color = java.awt.Color(Palette.LIGHT_PRIMARY_CONTAINER.toInt(), true)
    // RoundRectangle2D takes arc width/height, which is twice the corner radius, so s * 0.5f is the
    // 25% radius RoundedCornerShape(25) produces on the Compose side.
    g.fill(java.awt.geom.RoundRectangle2D.Float(0f, 0f, s.toFloat(), s.toFloat(), s * 0.5f, s * 0.5f))

    val scale = s / 480f
    g.translate(s / 2.0 - ART_CX * scale, s / 2.0 - ART_CY * scale)
    g.scale(scale.toDouble(), scale.toDouble())
    drawShieldArt(g)

    g.dispose()
    return image
}

/**
 * Draws the shield in raw art coordinates. Callers own the transform, so the coordinates below are
 * the canonical ones and can be diffed against `ic_vault_shield.xml` literally.
 */
private fun drawShieldArt(g: Graphics2D) {
    val tealDark = argb(LogoPalette.TEAL_DARK)
    val tealLight = argb(LogoPalette.TEAL_LIGHT)
    val innerLeft = argb(LogoPalette.INNER_LEFT)
    val innerRight = argb(LogoPalette.INNER_RIGHT)
    val circuitLeft = argb(LogoPalette.CIRCUIT_LEFT)
    val circuitRight = argb(LogoPalette.CIRCUIT_RIGHT)

    // ── Top Cap ──
    g.color = tealDark;  g.fill(tri(200f,135f, 169f,140f, 200f,170f))
    g.color = tealLight; g.fill(tri(200f,135f, 231f,140f, 200f,170f))

    // ── Outer Shield ──
    g.color = tealDark;  g.fill(svgPath("M 200 200 L 169 170 L 60 190 C 60 340 110 430 200 470 Z"))
    g.color = tealLight; g.fill(svgPath("M 200 200 L 231 170 L 340 190 C 340 340 290 430 200 470 Z"))

    // ── Inner Shield ──
    g.color = innerLeft;  g.fill(svgPath("M 200 200 L 85 215 C 85 330 125 410 200 440 Z"))
    g.color = innerRight; g.fill(svgPath("M 200 200 L 315 215 C 315 330 275 410 200 440 Z"))

    // ── Top V Detail ──
    g.color = tealDark;  g.fill(quad(100f,150f, 150f,150f, 200f,200f, 200f,250f))
    g.color = tealLight; g.fill(quad(300f,150f, 250f,150f, 200f,200f, 200f,250f))

    // ── Circuit Traces ──
    val stroke = BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    // Left traces + endpoint dots
    listOf(
        floatArrayOf(155f,260f, 130f,260f, 130f,230f)       to (130f to 230f),
        floatArrayOf(155f,285f, 115f,285f, 115f,310f)       to (115f to 310f),
        floatArrayOf(155f,310f, 140f,310f, 140f,340f, 120f,340f) to (120f to 340f),
        floatArrayOf(170f,335f, 170f,370f, 145f,370f)       to (145f to 370f),
        floatArrayOf(180f,345f, 180f,390f, 160f,390f)       to (160f to 390f),
    ).forEach { (pts, dot) ->
        g.color = circuitLeft; g.stroke = stroke; g.draw(polyline(pts))
        g.fill(circle(dot.first, dot.second, 3f))
    }
    // Left standalone dots (center-based)
    listOf(115f to 250f, 145f to 210f, 125f to 270f, 135f to 300f).forEach { (cx, cy) ->
        g.color = circuitLeft; g.fill(circle(cx, cy, 3f))
    }

    // Right traces + endpoint dots
    listOf(
        floatArrayOf(245f,260f, 270f,260f, 270f,230f)       to (270f to 230f),
        floatArrayOf(245f,285f, 285f,285f, 285f,310f)       to (285f to 310f),
        floatArrayOf(245f,310f, 260f,310f, 260f,340f, 280f,340f) to (280f to 340f),
        floatArrayOf(230f,335f, 230f,370f, 255f,370f)       to (255f to 370f),
        floatArrayOf(220f,345f, 220f,390f, 240f,390f)       to (240f to 390f),
    ).forEach { (pts, dot) ->
        g.color = circuitRight; g.stroke = stroke; g.draw(polyline(pts))
        g.fill(circle(dot.first, dot.second, 3f))
    }
    // Right standalone dots (center-based)
    listOf(285f to 250f, 255f to 210f, 275f to 270f, 265f to 300f).forEach { (cx, cy) ->
        g.color = circuitRight; g.fill(circle(cx, cy, 3f))
    }

    // ── Center Lock ──
    // Left: M 200 250 A 40 40 0 0 0 200 330 L 200 315 A 25 25 0 0 1 200 265 Z
    g.color = tealDark
    g.fill(GeneralPath().apply {
        moveTo(200f, 250f)
        svgArcTo(200f, 250f, 40f, 40f, 0f, largeArc = false, sweep = false, 200f, 330f)
        lineTo(200f, 315f)
        svgArcTo(200f, 315f, 25f, 25f, 0f, largeArc = false, sweep = true,  200f, 265f)
        closePath()
    })
    // Left keyhole: M 200 277 A 8 8 0 0 0 193.5 289.5 L 190 305 L 200 305 Z
    g.color = tealDark
    g.fill(GeneralPath().apply {
        moveTo(200f, 277f)
        svgArcTo(200f, 277f, 8f, 8f, 0f, largeArc = false, sweep = false, 193.5f, 289.5f)
        lineTo(190f, 305f); lineTo(200f, 305f); closePath()
    })

    // Right: M 200 250 A 40 40 0 0 1 200 330 L 200 315 A 25 25 0 0 0 200 265 Z
    g.color = tealLight
    g.fill(GeneralPath().apply {
        moveTo(200f, 250f)
        svgArcTo(200f, 250f, 40f, 40f, 0f, largeArc = false, sweep = true,  200f, 330f)
        lineTo(200f, 315f)
        svgArcTo(200f, 315f, 25f, 25f, 0f, largeArc = false, sweep = false, 200f, 265f)
        closePath()
    })
    // Right keyhole: M 200 277 A 8 8 0 0 1 206.5 289.5 L 210 305 L 200 305 Z
    g.color = tealLight
    g.fill(GeneralPath().apply {
        moveTo(200f, 277f)
        svgArcTo(200f, 277f, 8f, 8f, 0f, largeArc = false, sweep = true, 206.5f, 289.5f)
        lineTo(210f, 305f); lineTo(200f, 305f); closePath()
    })
}

// ── Geometry helpers ──

private fun tri(x1:Float,y1:Float, x2:Float,y2:Float, x3:Float,y3:Float) =
    GeneralPath().apply { moveTo(x1,y1); lineTo(x2,y2); lineTo(x3,y3); closePath() }

private fun quad(x1:Float,y1:Float, x2:Float,y2:Float, x3:Float,y3:Float, x4:Float,y4:Float) =
    GeneralPath().apply { moveTo(x1,y1); lineTo(x2,y2); lineTo(x3,y3); lineTo(x4,y4); closePath() }

/** Circle whose bounding box is centered on (cx, cy). */
private fun circle(cx: Float, cy: Float, r: Float) =
    Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2)

private fun polyline(pts: FloatArray) = GeneralPath().apply {
    moveTo(pts[0], pts[1])
    var i = 2; while (i < pts.size) { lineTo(pts[i], pts[i+1]); i += 2 }
}

/** Minimal SVG path parser supporting M, L, C, Z (space-separated, no commas). */
private fun svgPath(d: String): GeneralPath {
    val p = GeneralPath()
    val t = d.trim().split(' ')
    var i = 0
    while (i < t.size) {
        when (t[i++]) {
            "M" -> { p.moveTo(t[i].toFloat(), t[i+1].toFloat()); i += 2 }
            "L" -> { p.lineTo(t[i].toFloat(), t[i+1].toFloat()); i += 2 }
            "C" -> { p.curveTo(t[i].toFloat(), t[i+1].toFloat(),
                               t[i+2].toFloat(), t[i+3].toFloat(),
                               t[i+4].toFloat(), t[i+5].toFloat()); i += 6 }
            "Z", "z" -> p.closePath()
        }
    }
    return p
}

/**
 * Appends an SVG arc command (A) to this GeneralPath via cubic bezier approximation.
 * Implements the W3C SVG arc-to-center-parameterization algorithm.
 *
 * @param x1 current point x (start of arc)
 * @param y1 current point y (start of arc)
 * @param sweep false = counter-clockwise (SVG sweep-flag=0), true = clockwise (sweep-flag=1)
 */
private fun GeneralPath.svgArcTo(
    x1: Float, y1: Float,
    rx: Float, ry: Float,
    xRotationDeg: Float,
    largeArc: Boolean,
    sweep: Boolean,
    x2: Float, y2: Float
) {
    val phi = Math.toRadians(xRotationDeg.toDouble())
    val cosPhi = cos(phi); val sinPhi = sin(phi)

    val dx = (x1 - x2) / 2.0; val dy = (y1 - y2) / 2.0
    val x1p =  cosPhi * dx + sinPhi * dy
    val y1p = -sinPhi * dx + cosPhi * dy

    var rxA = abs(rx).toDouble(); var ryA = abs(ry).toDouble()
    val lambda = x1p * x1p / (rxA * rxA) + y1p * y1p / (ryA * ryA)
    if (lambda > 1) { val s = sqrt(lambda); rxA *= s; ryA *= s }

    val sign = if (largeArc == sweep) -1.0 else 1.0
    val num = rxA*rxA*ryA*ryA - rxA*rxA*y1p*y1p - ryA*ryA*x1p*x1p
    val den = rxA*rxA*y1p*y1p + ryA*ryA*x1p*x1p
    val sq  = sign * sqrt(max(0.0, num / den))

    val cxp = sq *  rxA * y1p / ryA
    val cyp = sq * -ryA * x1p / rxA
    val cx  = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0
    val cy  = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0

    val ux = (x1p - cxp) / rxA;  val uy = (y1p - cyp) / ryA
    val vx = (-x1p - cxp) / rxA; val vy = (-y1p - cyp) / ryA

    var theta1 = atan2(uy, ux)
    var dTheta = atan2(vy * ux - vx * uy, vx * ux + vy * uy)
    if (!sweep && dTheta > 0) dTheta -= 2 * PI
    if ( sweep && dTheta < 0) dTheta += 2 * PI

    val n = max(1, ceil(abs(dTheta) / (PI / 2)).toInt())
    val dStep = dTheta / n
    val alpha = (4.0 / 3.0) * tan(dStep / 4)

    var theta = theta1
    repeat(n) {
        val ct1 = cos(theta); val st1 = sin(theta)
        val ct2 = cos(theta + dStep); val st2 = sin(theta + dStep)

        val cp1x = (cosPhi * rxA * (ct1 - alpha*st1) - sinPhi * ryA * (st1 + alpha*ct1) + cx).toFloat()
        val cp1y = (sinPhi * rxA * (ct1 - alpha*st1) + cosPhi * ryA * (st1 + alpha*ct1) + cy).toFloat()
        val cp2x = (cosPhi * rxA * (ct2 + alpha*st2) - sinPhi * ryA * (st2 - alpha*ct2) + cx).toFloat()
        val cp2y = (sinPhi * rxA * (ct2 + alpha*st2) + cosPhi * ryA * (st2 - alpha*ct2) + cy).toFloat()
        val ex   = (cosPhi * rxA * ct2 - sinPhi * ryA * st2 + cx).toFloat()
        val ey   = (sinPhi * rxA * ct2 + cosPhi * ryA * st2 + cy).toFloat()

        curveTo(cp1x, cp1y, cp2x, cp2y, ex, ey)
        theta += dStep
    }
}
