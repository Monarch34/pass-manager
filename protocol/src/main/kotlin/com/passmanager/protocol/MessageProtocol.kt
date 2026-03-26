package com.passmanager.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PROTOCOL_V1 = 1

// ---------------------------------------------------------------------------
// Handshake messages (cleartext HTTP, pre-encryption)
// ---------------------------------------------------------------------------

@Serializable
data class HandshakeRequest(
    val phonePub: String,
    val token: String
)

@Serializable
data class HandshakeResponse(val ok: Boolean = true)

// ---------------------------------------------------------------------------
// Encrypted messages (inside EncryptedChannel envelopes)
// ---------------------------------------------------------------------------

@Serializable
sealed interface SecureRequest {

    @Serializable
    @SerialName("verify")
    data class Verify(val code: String) : SecureRequest

    @Serializable
    @SerialName("list_items")
    data object ListItems : SecureRequest

    @Serializable
    @SerialName("get_password")
    data class GetPassword(val itemId: String) : SecureRequest

    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(val ts: Long = System.currentTimeMillis()) : SecureRequest

    @Serializable
    @SerialName("disconnect")
    data object Disconnect : SecureRequest
}

@Serializable
sealed interface SecureResponse {

    @Serializable
    @SerialName("verify_ok")
    data class VerifyOk(val safetyNumber: String) : SecureResponse

    @Serializable
    @SerialName("verify_failed")
    data class VerifyFailed(val error: String, val attemptsRemaining: Int = 0) : SecureResponse

    @Serializable
    @SerialName("items")
    data class Items(val items: List<ItemSummary>) : SecureResponse

    @Serializable
    @SerialName("password")
    data class Password(val itemId: String, val password: ByteArray) : SecureResponse

    @Serializable
    @SerialName("heartbeat_ack")
    data class HeartbeatAck(val ts: Long = System.currentTimeMillis()) : SecureResponse

    @Serializable
    @SerialName("disconnect_ack")
    data object DisconnectAck : SecureResponse

    @Serializable
    @SerialName("error")
    data class Error(val error: String) : SecureResponse

    @Serializable
    @SerialName("rate_limited")
    data class RateLimited(val message: String) : SecureResponse
}

@Serializable
data class ItemSummary(
    val id: String,
    val title: String,
    val url: String = "",
    val category: String = "login"
)

@Serializable
data class PairingQrPayload(
    val v: Int = PROTOCOL_V1,
    val ip: String,
    val port: Int,
    val pub: String,
    val token: String
)
