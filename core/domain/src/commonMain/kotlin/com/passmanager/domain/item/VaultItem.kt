package com.passmanager.domain.item

import kotlinx.serialization.Serializable

/**
 * One entry in the vault.
 *
 * The identifier and the timestamps sit here rather than inside the payload, because they
 * are what a merge reasons about and the payload is what it moves. Version 1 kept a copy of
 * the identifier inside the payload as well and had to stamp it on the way out to keep the
 * two in step; there is one copy here.
 *
 * Timestamps are Unix epoch milliseconds, and both are inside the sealed body rather than
 * anywhere a reader can see before decrypting. A list of per-item modification times in the
 * clear is an item count and a diary of when the owner was at their desk.
 */
@Serializable
data class VaultItem(
    val id: ItemId,
    val createdAt: Long,
    /**
     * Changed by a content edit and by nothing else.
     *
     * Not by a rewrap, a key rotation, a re-encryption, or an attachment being added or
     * removed. This costs nothing to state now and cannot be recovered later: once a
     * desktop client exists, a device that merely rewrapped its vault would otherwise win
     * every merge, and attaching a scan on one device would silently discard a password
     * changed on the other.
     */
    val updatedAt: Long,
    val payload: ItemPayload,
) {
    val category: ItemCategory get() = payload.category

    /**
     * This item with a new payload, and [updatedAt] moved only if the payload actually
     * differs.
     *
     * Shared because both editors need exactly this rule and neither is the right place to
     * decide it. Opening an entry and pressing Save without typing anything is not a content
     * edit; stamping it as one would make this copy beat a device where the item genuinely
     * changed, which is precisely what [updatedAt]'s own documentation forbids.
     */
    fun edited(payload: ItemPayload, now: Long): VaultItem = VaultItem(
        id = id,
        createdAt = createdAt,
        updatedAt = if (payload == this.payload) updatedAt else now,
        payload = payload,
    )

    /**
     * Which of two versions of the same item survives a merge.
     *
     * Strictly newer, so re-importing a file you exported yourself changes nothing. The
     * timestamp is clamped to the present first: a file carrying a forged far-future time
     * would otherwise pin a stale item as the permanent winner of every future merge.
     */
    fun supersedes(other: VaultItem, now: Long): Boolean {
        require(id == other.id) { "merge compares one item against itself, not two items" }
        return minOf(updatedAt, now) > minOf(other.updatedAt, now)
    }
}
