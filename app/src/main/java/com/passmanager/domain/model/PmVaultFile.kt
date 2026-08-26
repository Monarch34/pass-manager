package com.passmanager.domain.model

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.PmVaultInvalidParametersException
import com.passmanager.domain.exception.PmVaultMalformedException
import com.passmanager.domain.exception.PmVaultUnsupportedVersionException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * One exported item. `category` duplicates `payload.type` for cheap scanning; `payload.type`
 * stays authoritative (`docs/FORMAT.md`).
 */
@Serializable
data class PmVaultItemJson(
    val id: String,
    val category: String,
    val createdAt: Long,
    val updatedAt: Long,
    val payload: ItemPayload
)

/** The decrypted body of a `.pmvault` file. */
@Serializable
data class PmVaultBodyJson(
    val version: Int,
    val exportedAt: Long,
    val items: List<PmVaultItemJson>
)

// The header is serialized through plain Int fields rather than [KdfParams] for two reasons:
// KdfParams' `init` would throw IllegalArgumentException from inside the deserializer on a hostile
// file (an untyped failure the caller cannot classify), and encodeDefaults must be on for the
// header while the body deliberately omits payload defaults.
@Serializable
private data class KdfHeaderJson(
    val memory: Int,
    val iterations: Int,
    val parallelism: Int,
    val hashLength: Int
)

@Serializable
private data class HeaderJson(
    val version: Int,
    val salt: String,
    val kdf: KdfHeaderJson
)

/**
 * Reader and writer for the `.pmvault` v1 container.
 *
 * ```
 * offset  size  field
 * 0       4     magic "PMVT"
 * 4       2     headerLen, unsigned 16-bit big-endian
 * 6       N     header, UTF-8 JSON
 * 6+N     12    iv
 * 6+N+12  rest  AES-256-GCM ciphertext || 16-byte tag, AAD = the first 6+N bytes verbatim
 * ```
 *
 * `docs/FORMAT.md` is normative; this object implements it and nothing more. Deriving the export
 * key is the caller's job, and [parse] deliberately returns before any derivation happens so the
 * pre-KDF validation gate cannot be bypassed.
 */
object PmVaultFile {

    const val VERSION = 1
    const val FILE_EXTENSION = "pmvault"
    const val MIME_TYPE = "application/octet-stream"

    const val MAGIC_LENGTH = 4
    const val HEADER_LENGTH_FIELD_LENGTH = 2
    /** magic + headerLen — the fixed part of the AAD prefix. */
    const val PREFIX_LENGTH = MAGIC_LENGTH + HEADER_LENGTH_FIELD_LENGTH
    const val IV_LENGTH = 12
    const val GCM_TAG_LENGTH = 16
    const val SALT_LENGTH = 16

    // Pre-KDF validation gate (docs/FORMAT.md).
    const val MAX_HEADER_LENGTH = 4096
    const val MIN_KDF_MEMORY_KIB = 8192
    const val MAX_KDF_MEMORY_KIB = 262_144
    /** Argon2's structural minimum: it needs 8 KiB per lane, so `memory >= 8 * parallelism`. */
    const val MIN_MEMORY_PER_LANE_KIB = 8
    const val MIN_KDF_ITERATIONS = 1
    const val MAX_KDF_ITERATIONS = 16
    const val MIN_KDF_PARALLELISM = 1
    const val MAX_KDF_PARALLELISM = 8
    const val KDF_HASH_LENGTH = 32

    private val MAGIC = byteArrayOf(0x50, 0x4D, 0x56, 0x54) // "PMVT"

    private val headerJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = false
    }

    /** A parsed, validated container. The body is still encrypted — no key was needed to get here. */
    class Parsed(
        val kdfParams: KdfParams,
        val salt: ByteArray,
        /** The first `6 + headerLen` bytes, verbatim: the AAD the body was sealed with. */
        val aad: ByteArray,
        val body: EncryptedData
    )

    /**
     * Builds `magic || headerLen || header` for [salt] and [params].
     * The result is both the start of the file and the AAD for its body.
     */
    fun headerBytes(salt: ByteArray, params: KdfParams): ByteArray {
        require(salt.size == SALT_LENGTH) { "salt must be $SALT_LENGTH bytes, was ${salt.size}" }
        val header = HeaderJson(
            version = VERSION,
            salt = Base64.getEncoder().encodeToString(salt),
            kdf = KdfHeaderJson(
                memory = params.memory,
                iterations = params.iterations,
                parallelism = params.parallelism,
                hashLength = params.hashLength
            )
        )
        val json = headerJson
            .encodeToString(HeaderJson.serializer(), header)
            .toByteArray(Charsets.UTF_8)
        check(json.size <= MAX_HEADER_LENGTH) { "header would exceed $MAX_HEADER_LENGTH bytes" }

        val out = ByteArray(PREFIX_LENGTH + json.size)
        MAGIC.copyInto(out, 0)
        out[MAGIC_LENGTH] = ((json.size ushr 8) and 0xFF).toByte()
        out[MAGIC_LENGTH + 1] = (json.size and 0xFF).toByte()
        json.copyInto(out, PREFIX_LENGTH)
        return out
    }

    /** Concatenates a [headerBytes] prefix with the body's IV and ciphertext into the final file. */
    fun assemble(headerBytes: ByteArray, body: EncryptedData): ByteArray {
        require(body.iv.size == IV_LENGTH) { "iv must be $IV_LENGTH bytes, was ${body.iv.size}" }
        val out = ByteArray(headerBytes.size + IV_LENGTH + body.ciphertext.size)
        headerBytes.copyInto(out, 0)
        body.iv.copyInto(out, headerBytes.size)
        body.ciphertext.copyInto(out, headerBytes.size + IV_LENGTH)
        return out
    }

    /**
     * Parses and fully validates [bytes] **without deriving anything**.
     *
     * @throws PmVaultMalformedException the bytes are not a container, or are truncated
     * @throws PmVaultUnsupportedVersionException the header declares another format version
     * @throws PmVaultInvalidParametersException the header is in range-violating territory
     */
    fun parse(bytes: ByteArray): Parsed {
        if (bytes.size < PREFIX_LENGTH) throw PmVaultMalformedException("file is too short")
        for (i in 0 until MAGIC_LENGTH) {
            if (bytes[i] != MAGIC[i]) throw PmVaultMalformedException("magic bytes do not match")
        }

        val headerLen = ((bytes[MAGIC_LENGTH].toInt() and 0xFF) shl 8) or
            (bytes[MAGIC_LENGTH + 1].toInt() and 0xFF)
        if (headerLen == 0) throw PmVaultMalformedException("header is empty")
        if (headerLen > MAX_HEADER_LENGTH) {
            throw PmVaultInvalidParametersException(
                "header length $headerLen exceeds $MAX_HEADER_LENGTH"
            )
        }

        val ivStart = PREFIX_LENGTH + headerLen
        val bodyStart = ivStart + IV_LENGTH
        if (bytes.size < bodyStart + GCM_TAG_LENGTH) {
            throw PmVaultMalformedException("file is truncated")
        }

        val headerText = String(bytes, PREFIX_LENGTH, headerLen, Charsets.UTF_8)
        val header = try {
            headerJson.decodeFromString(HeaderJson.serializer(), headerText)
        } catch (e: SerializationException) {
            throw PmVaultMalformedException("header is not valid JSON")
        } catch (e: IllegalArgumentException) {
            throw PmVaultMalformedException("header is not valid JSON")
        }

        if (header.version != VERSION) throw PmVaultUnsupportedVersionException(header.version)
        validateKdf(header.kdf)

        val salt = try {
            Base64.getDecoder().decode(header.salt)
        } catch (e: IllegalArgumentException) {
            throw PmVaultMalformedException("salt is not valid base64")
        }
        if (salt.size != SALT_LENGTH) {
            throw PmVaultInvalidParametersException(
                "salt must be $SALT_LENGTH bytes, was ${salt.size}"
            )
        }

        return Parsed(
            // Safe by construction: validateKdf already enforced ranges at least as tight as
            // KdfParams' own, so this constructor cannot throw here.
            kdfParams = KdfParams(
                memory = header.kdf.memory,
                iterations = header.kdf.iterations,
                parallelism = header.kdf.parallelism,
                hashLength = header.kdf.hashLength
            ),
            salt = salt,
            aad = bytes.copyOfRange(0, ivStart),
            body = EncryptedData(
                ciphertext = bytes.copyOfRange(bodyStart, bytes.size),
                iv = bytes.copyOfRange(ivStart, bodyStart)
            )
        )
    }

    /** Encodes the plaintext body. Payloads keep the exact at-rest [PayloadJson] shape. */
    fun encodeBody(body: PmVaultBodyJson): ByteArray =
        PayloadJson.instance
            .encodeToString(PmVaultBodyJson.serializer(), body)
            .toByteArray(Charsets.UTF_8)

    /** Decodes a decrypted body. Throws [PmVaultMalformedException] if it is not the v1 schema. */
    fun decodeBody(plaintext: ByteArray): PmVaultBodyJson {
        val body = try {
            PayloadJson.instance.decodeFromString(
                PmVaultBodyJson.serializer(),
                plaintext.toString(Charsets.UTF_8)
            )
        } catch (e: SerializationException) {
            throw PmVaultMalformedException("body is not valid JSON")
        } catch (e: IllegalArgumentException) {
            throw PmVaultMalformedException("body is not valid JSON")
        }
        if (body.version != VERSION) throw PmVaultUnsupportedVersionException(body.version)
        return body
    }

    /**
     * The DoS gate, in the order `docs/FORMAT.md` lists it.
     *
     * The `memory >= 8 * parallelism` rule is Argon2's own structural minimum: without it a header
     * can clear every other bound and still be rejected from inside the library, as a raw
     * IllegalArgumentException rather than one of this file's typed errors. At the pinned bounds it
     * is subsumed by the 8192 KiB floor — 8 * the maximum parallelism of 8 is only 64 KiB — so it
     * is carried for fidelity to the spec and for the day that floor moves, not because any header
     * reaches it today.
     */
    private fun validateKdf(kdf: KdfHeaderJson) {
        if (kdf.memory < MIN_KDF_MEMORY_KIB) {
            throw PmVaultInvalidParametersException(
                "kdf.memory ${kdf.memory} is below $MIN_KDF_MEMORY_KIB KiB"
            )
        }
        if (kdf.memory > MAX_KDF_MEMORY_KIB) {
            throw PmVaultInvalidParametersException(
                "kdf.memory ${kdf.memory} exceeds $MAX_KDF_MEMORY_KIB KiB"
            )
        }
        if (kdf.iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
            throw PmVaultInvalidParametersException(
                "kdf.iterations ${kdf.iterations} outside $MIN_KDF_ITERATIONS..$MAX_KDF_ITERATIONS"
            )
        }
        if (kdf.parallelism !in MIN_KDF_PARALLELISM..MAX_KDF_PARALLELISM) {
            throw PmVaultInvalidParametersException(
                "kdf.parallelism ${kdf.parallelism} outside $MIN_KDF_PARALLELISM..$MAX_KDF_PARALLELISM"
            )
        }
        if (kdf.memory < MIN_MEMORY_PER_LANE_KIB * kdf.parallelism) {
            throw PmVaultInvalidParametersException(
                "kdf.memory ${kdf.memory} is below Argon2's structural minimum of " +
                    "${MIN_MEMORY_PER_LANE_KIB * kdf.parallelism} KiB " +
                    "for parallelism ${kdf.parallelism}"
            )
        }
        if (kdf.hashLength != KDF_HASH_LENGTH) {
            throw PmVaultInvalidParametersException(
                "kdf.hashLength ${kdf.hashLength} must be $KDF_HASH_LENGTH"
            )
        }
    }
}
