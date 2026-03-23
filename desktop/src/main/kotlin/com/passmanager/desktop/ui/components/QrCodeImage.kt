package com.passmanager.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage

@Composable
fun QrCodeImage(
    content: String,
    size: Int = 280,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(content, size) { generateQrBitmap(content, size) }
    Image(
        bitmap = bitmap,
        contentDescription = "QR Code for pairing",
        modifier = modifier
    )
}

private fun generateQrBitmap(content: String, size: Int): ImageBitmap {
    val writer = QRCodeWriter()
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until size) {
        for (y in 0 until size) {
            image.setRGB(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return image.toComposeImageBitmap()
}
