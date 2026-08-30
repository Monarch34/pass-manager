package com.passmanager.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * A PDF rendered from memory, with the bytes never reaching the filesystem.
 *
 * ### Why this exists at all
 *
 * [PdfRenderer] takes a file descriptor and nothing else, and the descriptor has to be
 * seekable — it maps the document rather than streaming it, so `createPipe` is not an option.
 * The obvious way to satisfy it is to write the decrypted attachment into the cache directory
 * and hand over a descriptor for that. That would mean a password manager writing the
 * plaintext of a document its owner encrypted onto disk, where it survives a crash, a battery
 * pull, and any deletion that does not overwrite.
 *
 * `memfd_create` gives a descriptor backed by anonymous memory instead. It seeks, it maps,
 * `PdfRenderer` cannot tell the difference, and it has no name in any filesystem: nothing can
 * open it, and it ceases to exist when the last descriptor closes.
 *
 * ### The floor
 *
 * `memfd_create` reached the public API in Android 11, four releases above this
 * application's minimum. Below that there is no way to get a seekable descriptor without
 * touching storage, so [open] returns null and the viewer says it cannot show the document
 * rather than quietly writing it out. Refusing to preview a file is a smaller cost than
 * silently spilling it.
 */
class InMemoryPdf private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    /** A document may have exactly one page open at a time, so rendering is serialised. */
    private val oneAtATime = Mutex()

    private var closed = false

    /**
     * Zero once closed, rather than throwing.
     *
     * Locking the vault tears this screen down while the list of pages is still being laid
     * out, and `PdfRenderer` answers any question after `close` by throwing. A closed
     * document having no pages is the truthful answer and the one that does not crash the
     * application on the way to the lock screen.
     */
    val pageCount: Int get() = if (closed) 0 else renderer.pageCount

    /**
     * Draws one page at [widthPx], keeping its proportions.
     *
     * Onto white first: a PDF page's own background is usually nothing at all, and a
     * transparent bitmap composited over a dark theme renders black text on black.
     */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = oneAtATime.withLock {
        if (closed) return@withLock null
        runCatching {
            renderer.openPage(index).use { page ->
                val height = (widthPx.toLong() * page.height / page.width)
                    .toInt().coerceIn(1, 8192)
                val bitmap = createBitmap(widthPx, height)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }.getOrNull()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /**
         * Null when this device cannot do it without a file, or when the bytes are not a PDF
         * this platform will open — an encrypted one, or a damaged one.
         */
        fun open(bytes: ByteArray): InMemoryPdf? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

            val memory = runCatching { Os.memfd_create("pm-attachment", 0) }.getOrNull() ?: return null
            val descriptor = try {
                val buffer = ByteBuffer.wrap(bytes)
                // Os.write is not obliged to take everything in one call.
                while (buffer.hasRemaining()) {
                    if (Os.write(memory, buffer) <= 0) return null
                }
                Os.lseek(memory, 0, OsConstants.SEEK_SET)
                ParcelFileDescriptor.dup(memory)
            } catch (error: Throwable) {
                return null
            } finally {
                // The duplicate is the one that is kept; this one has done its job either way.
                runCatching { Os.close(memory) }
            }

            return runCatching { InMemoryPdf(descriptor, PdfRenderer(descriptor)) }
                .getOrElse {
                    runCatching { descriptor.close() }
                    null
                }
        }
    }
}
