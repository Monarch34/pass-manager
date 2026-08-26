package com.passmanager.domain.exception

/**
 * Too many failed unlocks; the attempt was refused before any key derivation ran.
 *
 * @param remainingMs how much longer the caller must wait, so the lock screen can count down
 *   instead of just saying no.
 */
class UnlockThrottledException(val remainingMs: Long) :
    Exception("Too many failed attempts; wait ${remainingMs}ms")
