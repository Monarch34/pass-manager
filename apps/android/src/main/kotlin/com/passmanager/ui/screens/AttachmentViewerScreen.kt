package com.passmanager.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.passmanager.crypto.Secret
import com.passmanager.data.InMemoryPdf
import com.passmanager.ui.VaultViewModel
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.SectionFootnote
import com.passmanager.vault.Attachment
import com.passmanager.vault.AttachmentKind
import com.passmanager.vault.AttachmentKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An attachment, on screen, decrypted for exactly as long as it is being looked at.
 *
 * ### Nothing is written out to show it
 *
 * The decrypted bytes exist in one place: a [Secret] this screen owns and destroys when it
 * leaves. No temporary file, no cache copy, no handing the document to another application —
 * an image is decoded from the array, and a PDF is rendered through a descriptor backed by
 * anonymous memory. The cost of that choice is the honest message at the bottom of this file:
 * what cannot be drawn here is not shown at all.
 *
 * ### Why this is not a dialog
 *
 * `FLAG_SECURE` is a property of a window, and a Compose `Dialog` is its own window. Shown
 * that way, the one screen in the application displaying a scan of someone's passport would
 * be the one screen a screenshot could capture. It is ordinary content in the activity's own
 * window instead.
 *
 * A bitmap on the screen is still plaintext in memory that nothing can wipe — that is what
 * drawing something means. What is avoided is a second copy that outlives the looking.
 */
@Composable
fun AttachmentViewerScreen(
    model: VaultViewModel,
    attachment: Attachment,
    onBack: () -> Unit,
) {
    val content = remember(attachment.id) { model.openAttachment(attachment.id) }
    DisposableEffect(attachment.id) {
        onDispose { content?.destroy() }
    }

    val preview by produceState<Preview>(Preview.Loading, attachment.id) {
        val secret = content
        value = if (secret == null) {
            Preview.Unavailable("This attachment could not be opened.")
        } else {
            // Decoding a five-megabyte photograph is not main-thread work, and neither is
            // parsing a PDF. The bytes are borrowed for the length of the call and the
            // result is a bitmap, never a second copy of the file.
            withContext(Dispatchers.Default) { secret.reveal { render(attachment, it) } }
        }
    }

    // Read into a local before the effect, and this is not a style preference.
    //
    // `preview` is a delegated read of the state, so a lambda mentioning it evaluates it when
    // the lambda runs — and `onDispose` runs *after* the state has already moved on. Written
    // as `DisposableEffect(preview) { onDispose { preview... } }`, the disposal of the
    // Loading effect closed the document that had just replaced it, and the list crashed on
    // the next line with "Document already closed". Capturing the value fixes the effect to
    // the object it is actually responsible for.
    val shown = preview
    DisposableEffect(shown) {
        // The descriptor it holds is the last reference to the anonymous memory the document
        // lives in, so closing it is what frees the plaintext.
        onDispose { (shown as? Preview.Document)?.pdf?.close() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onBack) { Text("Done") }
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    attachment.filename.ifEmpty { "Attachment" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    readableSize(attachment.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (shown) {
                Preview.Loading -> CircularProgressIndicator()
                is Preview.Picture -> ZoomableImage(shown.image, attachment.filename)
                is Preview.Document -> PdfPages(shown.pdf)
                is Preview.Words -> Words(shown)
                is Preview.Unavailable -> Unavailable(shown.reason, attachment.mimeType)
            }
        }
    }
}

/**
 * The image, fitted to the screen and then movable.
 *
 * Pinch and drag rather than a fixed fit, because the reason to attach a photograph of a
 * document to a password entry is usually a number printed small on it.
 */
@Composable
private fun ZoomableImage(image: ImageBitmap, filename: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Image(
        bitmap = image,
        // A screen reader has nothing else to go on here: the picture is the whole screen,
        // and its name is the only thing about it this application knows.
        contentDescription = filename.ifEmpty { "Attachment" },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        // Back at fit, the image belongs in the middle again rather than
                        // wherever it was dragged while zoomed.
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
    )
}

/**
 * The document, one page per row, each drawn only once it is scrolled to.
 *
 * A rendered page is a full-size bitmap; rendering all of a fifty-page statement at once
 * would cost hundreds of megabytes for the sake of pages nobody is looking at.
 */
@Composable
private fun PdfPages(pdf: InMemoryPdf) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) {
            maxWidth.toPx().toInt().coerceIn(1, MaxPageWidth)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pdf.pageCount) { index ->
                val page by produceState<ImageBitmap?>(null, index, widthPx) {
                    value = pdf.render(index, widthPx)?.asImageBitmap()
                }
                val rendered = page
                if (rendered == null) {
                    // Reserves roughly a page of height so the list does not jump as each
                    // one finishes drawing.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    Image(
                        bitmap = rendered,
                        contentDescription = "Page ${index + 1} of ${pdf.pageCount}",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}

@Composable
private fun Words(words: Preview.Words) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            words.text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        if (words.truncated) {
            SectionFootnote(
                "Only the first part is shown. The whole file is still stored.",
                Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun Unavailable(reason: String, mimeType: String) {
    PanelCard(Modifier.padding(16.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(reason, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Images, PDFs and text can be shown here. Anything else stays sealed rather " +
                    "than being written out for another app to open.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mimeType.isNotEmpty()) SectionFootnote(mimeType)
        }
    }
}

private sealed interface Preview {
    data object Loading : Preview
    class Picture(val image: ImageBitmap) : Preview
    class Document(val pdf: InMemoryPdf) : Preview
    class Words(val text: String, val truncated: Boolean) : Preview
    class Unavailable(val reason: String) : Preview
}

/** Four megapixels: more than a phone screen resolves, and 16 MB of bitmap at the ceiling. */
private const val MaxPixels = 4 * 1024 * 1024

/** Rendering wider than this serves nothing and costs memory in proportion. */
private const val MaxPageWidth = 2048

/** Enough text to read; a five-megabyte log laid out in one composable is not readable. */
private const val MaxCharacters = 256 * 1024

private fun render(attachment: Attachment, bytes: ByteArray): Preview {
    val prefix = bytes.copyOf(minOf(AttachmentKinds.SniffSize, bytes.size))
    return when (AttachmentKinds.of(attachment.mimeType, prefix)) {
        AttachmentKind.Image -> decodeBounded(bytes)?.let { Preview.Picture(it.asImageBitmap()) }
            // A kind this recognises but the platform cannot decode — an SVG, most often,
            // which is an image everywhere except to BitmapFactory.
            ?: Preview.Unavailable("This image is in a format the phone cannot draw.")

        AttachmentKind.Pdf -> InMemoryPdf.open(bytes)?.let { Preview.Document(it) }
            ?: Preview.Unavailable("This PDF could not be opened.")

        AttachmentKind.Text -> {
            val text = bytes.decodeToString()
            Preview.Words(text.take(MaxCharacters), truncated = text.length > MaxCharacters)
        }

        AttachmentKind.Opaque -> Preview.Unavailable("There is no viewer here for this file.")
    }
}

/**
 * Decodes at a bounded size.
 *
 * The dimensions are read first and the decode is sampled down to fit [MaxPixels]. A photo
 * from a recent phone is fifty megapixels, which as a bitmap is two hundred megabytes — quite
 * enough to end the process while looking at a picture of a passport.
 */
private fun decodeBounded(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options()
    var sample = 1
    while (
        (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > MaxPixels
    ) {
        sample *= 2
    }
    options.inSampleSize = sample
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
}
