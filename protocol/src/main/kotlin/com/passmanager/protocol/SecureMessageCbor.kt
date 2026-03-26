@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.passmanager.protocol

import kotlinx.serialization.cbor.Cbor

/**
 * CBOR serialization for [SecureRequest] / [SecureResponse] over the encrypted WebSocket.
 */
object SecureMessageCbor {
    val cbor: Cbor = Cbor {
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
