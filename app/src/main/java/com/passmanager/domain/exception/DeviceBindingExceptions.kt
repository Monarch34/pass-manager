package com.passmanager.domain.exception

/**
 * The device key that seals a v2 vault's outer layer is **gone for good**.
 *
 * Raised only for the two states the Keystore never recovers from: the alias no longer exists, or
 * the key was permanently invalidated. There is no retry that helps and no passphrase that
 * substitutes — the only way forward is resetting the vault and restoring a `.pmvault` backup, so
 * this must route to the recovery screen and nowhere else.
 */
class DeviceKeyLostException(cause: Throwable? = null) :
    Exception("The device key for this vault is no longer available", cause)

/**
 * The device key could not be used **right now**.
 *
 * Everything that is not one of the two permanent states above lands here: a locked device, a
 * busy or wedged keymaster, an OEM Keystore hiccup. These recover on their own, so the user is
 * told to try again — never that their vault is lost. Showing the recovery screen on a transient
 * fault would talk people into wiping a perfectly intact vault.
 */
class DeviceKeyUnavailableException(cause: Throwable? = null) :
    Exception("The device key could not be used right now", cause)
