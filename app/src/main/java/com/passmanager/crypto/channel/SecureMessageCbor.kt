@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.passmanager.crypto.channel

import kotlinx.serialization.cbor.Cbor

/**
 * CBOR serialization for [SecureRequest] / [SecureResponse] over the encrypted WebSocket.
 * Binary format avoids UTF-8 decoding entire payloads into [String] and encodes passwords as raw bytes.
 */
internal object SecureMessageCbor {
    val cbor: Cbor = Cbor {
        // Include default field values so peers with identical schema always round-trip predictably.
        encodeDefaults = true
    }

    fun encodeRequest(request: SecureRequest): ByteArray =
        cbor.encodeToByteArray(SecureRequest.serializer(), request)

    fun decodeRequest(bytes: ByteArray): SecureRequest =
        cbor.decodeFromByteArray(SecureRequest.serializer(), bytes)

    fun encodeResponse(response: SecureResponse): ByteArray =
        cbor.encodeToByteArray(SecureResponse.serializer(), response)

    fun decodeResponse(bytes: ByteArray): SecureResponse =
        cbor.decodeFromByteArray(SecureResponse.serializer(), bytes)
}
