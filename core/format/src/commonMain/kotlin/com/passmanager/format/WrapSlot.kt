package com.passmanager.format

import com.passmanager.crypto.key.VaultKeys

/**
 * One way into the vault: a copy of the vault key, wrapped under some other key.
 *
 * A list rather than a fixed pair of fields, because version 1 already paid for getting
 * this wrong. It began with one wrapped key, grew a second column pair for the biometric
 * copy, then a third for a pepper, then a version enum to say which layers were active —
 * four migrations to express "there is more than one way to unlock this". The shape is
 * observed, not anticipated.
 *
 * The length prefix is what makes the list survive: a reader that meets a kind it does not
 * know can step over it and try the ones it does, so a future keystore-backed slot with a
 * completely different body does not make the file unreadable to anything already shipped.
 *
 * ### Deliberately outside the body's associated data
 *
 * The wrap block is not covered by any tag over the body, and that is not an oversight.
 * `VaultKeys.wrap` draws a fresh nonce on every call, so re-wrapping the *same* vault key
 * under the *same* key produces different bytes. Were this block inside the body's
 * associated data, every re-wrap would invalidate the body's tag — and changing a passphrase
 * would become a full decrypt-and-re-encrypt of the whole vault, which is the exact cost the
 * two-key model exists to avoid. Adding a biometric slot would re-seal the vault too.
 *
 * Nothing is lost by leaving it out. Each slot's body is itself authenticated under its own
 * key with its own context string, so editing one makes it fail to unwrap. Editing the
 * descriptor's salt or costs simply derives a different key, which also fails to unwrap.
 * Tampering here is denial of service, not forgery.
 *
 * **The residual, stated rather than papered over:** because the block is not bound to the
 * body, someone holding the file can put back a wrap block they captured earlier and reopen
 * the vault with an old passphrase. There is no fix without state kept somewhere the file is
 * not, which a local-first application does not have — and an attacker able to swap the
 * block could equally have kept the entire old file. A counter here would look like a defence
 * and be none.
 */
class WrapSlot(val kind: Int, val body: ByteArray) {

    init {
        require(kind in 0..255) { "slot kind $kind does not fit in a byte" }
        require(body.size <= MaxBodySize) { "slot body is ${body.size} bytes; the limit is $MaxBodySize" }
    }

    /** The encoded size: kind, length, body. */
    internal val encodedSize: Int get() = 3 + body.size

    companion object {
        /**
         * Unlocked by a passphrase. The body is the 60-byte wrapped key from
         * `VaultKeys.wrap`; the salt and cost parameters come from the descriptor rather
         * than being repeated here.
         */
        const val KindPassphrase = 1

        /** Enough for every unlock path a device can plausibly offer at once. */
        const val MaxSlots = 4

        private const val MaxBodySize = 4096

        /**
         * The slot an export carries, and the only one.
         *
         * An export must never carry the device's own vault key. Wrapping the same key for
         * a different recipient would make every export ever taken, together with its
         * passphrase, a permanent door into the current vault — revocable only by
         * re-encrypting everything. An export seals its body under a freshly drawn key
         * instead, so it is a snapshot rather than a spare key.
         */
        fun passphrase(wrappedVaultKey: ByteArray): WrapSlot {
            require(wrappedVaultKey.size == VaultKeys.WrappedSize) {
                "a wrapped vault key is ${VaultKeys.WrappedSize} bytes, not ${wrappedVaultKey.size}"
            }
            return WrapSlot(KindPassphrase, wrappedVaultKey)
        }
    }
}
