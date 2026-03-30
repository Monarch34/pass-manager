package com.passmanager.domain.exception

/** Thrown when a supplied passphrase does not decrypt the vault key. */
class WrongPassphraseException : Exception("Incorrect passphrase")

/** Thrown when the Keystore biometric key has been permanently invalidated. */
class BiometricKeyInvalidatedException :
    Exception("Biometric key was invalidated — please unlock with your passphrase")
