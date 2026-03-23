package com.passmanager.domain.usecase

/** Handshake HTTP returned an error body (e.g. 409 already_paired, 500 server error). */
class DesktopHandshakeException(message: String) : Exception(message)
