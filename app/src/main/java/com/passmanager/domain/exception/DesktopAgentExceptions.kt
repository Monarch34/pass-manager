package com.passmanager.domain.exception

/** Handshake HTTP returned an error body (e.g. 409 already_paired, 500 server error). */
class DesktopHandshakeException(message: String) : Exception(message)

/** Thrown when a desktop password request is rejected by the rate limiter. */
class DesktopRateLimitException : Exception("Password request rate limited")

/** Thrown when a replay attack is detected on the encrypted channel. */
class ReplayAttackException(message: String) : SecurityException(message)
