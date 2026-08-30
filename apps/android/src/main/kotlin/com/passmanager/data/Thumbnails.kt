package com.passmanager.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.passmanager.vault.VaultSession
import java.io.ByteArrayOutputStream

/**
 * A small picture of an attachment, for the row that lists it.
 *
 * Only for images, and only for images the platform can decode. A document has no thumbnail
 * here — rendering the first page of a PDF to make one would mean opening the document on
 * every attach, which is a lot of work for a row that already says what the file is called.
 *
 * The result travels inside the attachment's own sealed header, so it is encrypted exactly
 * like the file it depicts. That is the reason to generate it at all rather than reading the
 * attachment back to draw a list: the header is a few kilobytes and the attachment can be
 * five megabytes.
 */
object Thumbnails {

    /** Big enough for a list row on a dense screen, small enough to encode to a few KB. */
    private const val MaxEdge = 192

    /**
     * Quality is low on purpose. This is a thumbnail whose whole budget is
     * [VaultSession.MaxThumbnailSize], and every attachment's copy of it is read whenever an
     * item is opened.
     */
    private const val Quality = 55

    /** Null when the bytes are not an image, or when no small enough version could be made. */
    fun of(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options()
        var sample = 1
        // Decode no larger than roughly twice the target: sampling is in powers of two, so
        // this lands within one step of the size actually wanted and never decodes a fifty
        // megapixel photograph in full to make a thumbnail of it.
        while (
            (bounds.outWidth / sample) > MaxEdge * 2 && (bounds.outHeight / sample) > MaxEdge * 2
        ) {
            sample *= 2
        }
        options.inSampleSize = sample

        val decoded = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull() ?: return null

        val scale = MaxEdge.toFloat() / maxOf(decoded.width, decoded.height)
        val scaled = if (scale >= 1f) decoded else decoded.scale(
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
        )

        val out = ByteArrayOutputStream()
        // JPEG rather than WebP or PNG: it is the only one every Android version this
        // application supports compresses at a predictable size, and a photograph of a
        // document is exactly what it is good at.
        scaled.compress(Bitmap.CompressFormat.JPEG, Quality, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        return out.toByteArray().takeIf { it.size <= VaultSession.MaxThumbnailSize }
    }
}
