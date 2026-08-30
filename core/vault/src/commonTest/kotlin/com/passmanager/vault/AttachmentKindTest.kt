package com.passmanager.vault

import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentKindTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun kind(declared: String, prefix: ByteArray) = AttachmentKinds.of(declared, prefix)

    private fun kind(declared: String, prefix: String) =
        AttachmentKinds.of(declared, prefix.encodeToByteArray())

    /** A container's leading length field, which is binary and is why these are not text. */
    private fun boxed(size: Int, tag: String) = bytes(0, 0, 0, size) + tag.encodeToByteArray()

    @Test
    fun `a signature is believed over the declared type`() {
        // The case that matters: the first iOS build recorded octet-stream for everything it
        // stored, so those attachments are viewable only if the bytes are what decides.
        assertEquals(
            AttachmentKind.Image,
            kind("application/octet-stream", bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
        )
        assertEquals(AttachmentKind.Pdf, kind("application/octet-stream", "%PDF-1.7\n%abc"))
    }

    @Test
    fun `a declared type that contradicts the bytes does not win`() {
        // A viewer picked from a string is a decoder picked by whoever wrote the string.
        assertEquals(AttachmentKind.Pdf, kind("image/png", "%PDF-1.4 "))
        assertEquals(
            AttachmentKind.Image,
            kind("application/pdf", bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)),
        )
    }

    @Test
    fun `the image signatures`() {
        assertEquals(AttachmentKind.Image, kind("", "GIF89a"))
        assertEquals(AttachmentKind.Image, kind("", "BM______"))
        assertEquals(AttachmentKind.Image, kind("", "RIFF".encodeToByteArray() +
            bytes(0x24, 0x08, 0x00, 0x00) + "WEBPVP8 ".encodeToByteArray()))
        assertEquals(AttachmentKind.Image, kind("", bytes(0x49, 0x49, 0x2A, 0x00, 0x08)))
        assertEquals(AttachmentKind.Image, kind("", bytes(0x4D, 0x4D, 0x00, 0x2A, 0x00)))
        assertEquals(AttachmentKind.Image, kind("", boxed(0x18, "ftypheic")))
        assertEquals(AttachmentKind.Image, kind("", boxed(0x18, "ftypmif1")))
    }

    @Test
    fun `a RIFF container that is not WEBP is not an image`() {
        // WAVE is the common one, and a picture decoder handed a sound file draws nothing.
        assertEquals(AttachmentKind.Opaque, kind("", "RIFF".encodeToByteArray() +
            bytes(0x24, 0x08, 0x00, 0x00) + "WAVEfmt ".encodeToByteArray()))
    }

    @Test
    fun `an ISO container holding film is not an image`() {
        assertEquals(AttachmentKind.Opaque, kind("", boxed(0x18, "ftypisom")))
        assertEquals(AttachmentKind.Opaque, kind("", boxed(0x18, "ftypmp42")))
    }

    @Test
    fun `a signature has to be at the start`() {
        assertEquals(AttachmentKind.Opaque, kind("", bytes(0x00, 0x00, 0xFF, 0xD8, 0xFF)))
    }

    @Test
    fun `the declared type decides when nothing is recognised`() {
        assertEquals(AttachmentKind.Image, kind("image/svg+xml", bytes(0x00, 0x01, 0x02)))
        assertEquals(AttachmentKind.Text, kind("text/csv; charset=utf-8", bytes(0x00, 0x01)))
        assertEquals(AttachmentKind.Text, kind("APPLICATION/JSON", bytes(0x00, 0x01)))
    }

    @Test
    fun `unrecognised bytes that read as text are text`() {
        assertEquals(AttachmentKind.Text, kind("application/octet-stream", "one-time codes\n4821"))
        // Turkish, because the alphabet this project's first users type in is exactly where
        // a byte-wise "is it printable" check would have gone wrong.
        assertEquals(AttachmentKind.Text, kind("application/octet-stream", "Şifreler: İstanbul"))
    }

    @Test
    fun `a prefix cut through a character is still text`() {
        val whole = "Şifreler".encodeToByteArray()
        // One byte short of a complete two-byte character, which is what reading a fixed
        // number of bytes off the front of a file produces.
        assertEquals(AttachmentKind.Text, kind("", whole.copyOf(whole.size - 1)))
    }

    @Test
    fun `binary that is not a known format is opaque`() {
        assertEquals(AttachmentKind.Opaque, kind("", bytes(0x00, 0x01, 0x02, 0x03, 0xFF)))
        // A ZIP, which is what an unknown attachment usually is.
        assertEquals(AttachmentKind.Opaque, kind("", bytes(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)))
    }

    @Test
    fun `an empty attachment is opaque rather than text`() {
        assertEquals(AttachmentKind.Opaque, kind("application/octet-stream", ByteArray(0)))
    }
}
