@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.passmanager.desktop.crypto

import com.passmanager.desktop.model.SecureRequest
import com.passmanager.desktop.model.SecureResponse
import kotlinx.serialization.cbor.Cbor

/**
 * Must stay in sync with Android [com.passmanager.crypto.channel.SecureMessageCbor].
 */
internal object SecureMessageCbor {
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
