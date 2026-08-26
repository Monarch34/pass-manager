package com.passmanager.domain.exception

/**
 * Failures raised while reading a `.pmvault` export container.
 *
 * Every subclass except [PmVaultAuthenticationException] is decided *before* the export key is
 * derived, so a hostile file can never make the app spend Argon2 time on it. See `docs/FORMAT.md`
 * — that document is normative for the checks these exceptions report.
 */
sealed class PmVaultException(message: String) : Exception(message)

/** The bytes are not a `.pmvault` container at all: bad magic, truncated, or an unparseable header. */
class PmVaultMalformedException(detail: String) :
    PmVaultException("Not a valid .pmvault file: $detail")

/** The container parses but declares a version this build does not know how to read. */
class PmVaultUnsupportedVersionException(val version: Int) :
    PmVaultException("Unsupported .pmvault version: $version")

/**
 * The header is well-formed JSON but carries values outside the ranges `docs/FORMAT.md` allows —
 * the DoS gate that stops a crafted header from demanding a 1 GiB derivation.
 */
class PmVaultInvalidParametersException(detail: String) :
    PmVaultException("Rejected .pmvault header: $detail")

/**
 * The body failed its GCM tag check. AES-GCM cannot tell a wrong passphrase from a corrupted or
 * tampered file, so neither can this message — see `docs/FORMAT.md`.
 */
class PmVaultAuthenticationException :
    PmVaultException("The passphrase is wrong or the file is corrupted")
