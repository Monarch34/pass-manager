package com.passmanager.vault

/**
 * What an attachment is, as far as showing it to someone is concerned.
 *
 * Deliberately coarse. This is not a file-type database — it answers one question, "which
 * viewer draws this", and every answer it can give corresponds to something both applications
 * can actually render from memory.
 */
enum class AttachmentKind {
    Image,
    Pdf,
    Text,

    /** Nothing here can draw it. Honest, and the only correct answer for an archive. */
    Opaque,
}

/**
 * Decides what an attachment is by looking at it.
 *
 * ### Why the bytes decide and the declared type only breaks ties
 *
 * The MIME type in a blob's header is whatever the system that handed the file over claimed,
 * and that claim is often `application/octet-stream` — every attachment the first iOS build
 * stored says exactly that, because it never asked. Sniffing is the only thing that makes
 * those viewable, and it is the more truthful answer in general: a file's first eight bytes
 * are what a decoder will act on, and a string in a header is not.
 *
 * It also closes a small hole. The header is authenticated, so nobody can rewrite the type of
 * an attachment already stored — but the type comes from outside at the moment of attaching,
 * and dispatching a decoder on an outsider's say-so is the shape of a great many image
 * library bugs. Handing PNG bytes to a PNG decoder because they begin with a PNG signature is
 * narrower than handing them over because a string said to.
 */
object AttachmentKinds {

    /**
     * Enough to recognise every signature below with room to spare, and enough of a text file
     * to tell text from a binary that happens to begin with printable bytes.
     */
    const val SniffSize = 1024

    fun of(declaredType: String, prefix: ByteArray): AttachmentKind {
        signature(prefix)?.let { return it }
        fromDeclaredType(declaredType)?.let { return it }
        // Last: a file with no signature this knows and no useful declared type is still
        // readable if it is text, and much of what people attach — recovery codes, a public
        // key, an exported CSV — is exactly that.
        return if (looksLikeText(prefix)) AttachmentKind.Text else AttachmentKind.Opaque
    }

    /**
     * The leading bytes, for the formats worth recognising.
     *
     * Checked at offset zero, not searched for. A signature found somewhere inside a file is
     * evidence of nothing — every decoder here starts reading at the beginning.
     */
    private fun signature(bytes: ByteArray): AttachmentKind? = when {
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> AttachmentKind.Image
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> AttachmentKind.Image
        bytes.matchesAt(0, "GIF8") -> AttachmentKind.Image
        bytes.matchesAt(0, "BM") -> AttachmentKind.Image
        // RIFF is a container for several things; only WEBP is an image.
        bytes.matchesAt(0, "RIFF") && bytes.matchesAt(8, "WEBP") -> AttachmentKind.Image
        // TIFF, both byte orders. What a flatbed scanner tends to produce.
        bytes.startsWith(0x49, 0x49, 0x2A, 0x00) -> AttachmentKind.Image
        bytes.startsWith(0x4D, 0x4D, 0x00, 0x2A) -> AttachmentKind.Image
        // ISO base media: a size, then "ftyp", then a brand. HEIC is what an iPhone camera
        // writes, so a photograph of a document usually lands here.
        bytes.matchesAt(4, "ftyp") && bytes.isoBrandIsImage() -> AttachmentKind.Image
        bytes.matchesAt(0, "%PDF-") -> AttachmentKind.Pdf
        else -> null
    }

    private fun fromDeclaredType(declaredType: String): AttachmentKind? {
        val type = declaredType.substringBefore(';').trim().lowercase()
        return when {
            type == "application/pdf" -> AttachmentKind.Pdf
            type.startsWith("image/") -> AttachmentKind.Image
            type.startsWith("text/") -> AttachmentKind.Text
            // The two that are text wearing an application/ prefix.
            type == "application/json" || type == "application/xml" -> AttachmentKind.Text
            else -> null
        }
    }

    /**
     * Whether a prefix decodes as UTF-8 and holds nothing a text viewer would draw as a box.
     *
     * Trailing bytes are dropped one at a time before giving up, because this is a prefix and
     * a cut through the middle of a character is not evidence that the file is binary. Three
     * is the most a UTF-8 sequence can be missing.
     */
    private fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        for (trimmed in 0..minOf(3, bytes.size - 1)) {
            val text = runCatching {
                bytes.decodeToString(0, bytes.size - trimmed, throwOnInvalidSequence = true)
            }.getOrNull() ?: continue
            return text.none { it.isBinaryControl() }
        }
        return false
    }

    /** Tab and the two line endings are text; every other control character is not. */
    private fun Char.isBinaryControl(): Boolean =
        this != '\t' && this != '\n' && this != '\r' && (this < ' ' || code == 0x7F)

    private fun ByteArray.startsWith(vararg expected: Int): Boolean {
        if (size < expected.size) return false
        for (i in expected.indices) if ((this[i].toInt() and 0xff) != expected[i]) return false
        return true
    }

    private fun ByteArray.matchesAt(offset: Int, text: String): Boolean {
        if (size < offset + text.length) return false
        for (i in text.indices) {
            if ((this[offset + i].toInt() and 0xff) != text[i].code) return false
        }
        return true
    }

    /**
     * Which ISO base media brands are still pictures.
     *
     * The `heic`/`heix`/`hevc`/`hevx` family are, and so are `mif1`, `msf1` and `avif`.
     * `isom`, `mp42` and `qt` are film, and calling a film an image only produces a viewer
     * showing nothing.
     */
    private fun ByteArray.isoBrandIsImage(): Boolean =
        matchesAt(8, "heic") || matchesAt(8, "heix") ||
            matchesAt(8, "hevc") || matchesAt(8, "hevx") ||
            matchesAt(8, "mif1") || matchesAt(8, "msf1") ||
            matchesAt(8, "avif")
}
