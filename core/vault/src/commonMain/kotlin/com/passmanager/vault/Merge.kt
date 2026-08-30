package com.passmanager.vault

import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.VaultItem
import com.passmanager.format.VaultBody
import com.passmanager.format.VaultContents

/**
 * Combining a file with a vault, by one rule.
 *
 * **For every identifier, the side whose last event is newer wins, and the destination wins
 * ties.** An item's last event is its `updatedAt`; a deletion's is its `deletedAt`; and
 * absence is not an event at all, so a file that simply does not mention an item never
 * removes it. Every case below falls out of that sentence rather than being decided
 * separately, which is the only way a merge stays reviewable.
 *
 * Ties going to the destination is what makes re-importing a file you exported yourself do
 * nothing at all.
 *
 * ### Timestamps are clamped on the way in, not at comparison
 *
 * [VaultItem.supersedes] clamps to the present when comparing, which stops a forged
 * far-future time from winning *forever* but not from winning *now*: `min(2099, now)` is
 * `now`, and `now` beats every genuine past edit. Clamping as the file is read freezes the
 * forged value at the moment of import, so the next real edit on this device beats it.
 * A file cannot be trusted about when its own contents were written.
 *
 * ### A merge chooses; it never edits
 *
 * For each identifier the result is one of the two payloads, whole. Nothing is combined,
 * averaged, or spliced. It is tempting to make an exception — folding the losing side's
 * `previousPasswords` into the winner's would lose strictly less — and it is still wrong: a
 * payload that came out of a merge is one that neither device ever wrote, which no test on
 * either device covers and no user ever chose. Losing an old password is recoverable;
 * inventing an entry is not.
 */
internal object Merge {

    fun of(
        destination: VaultContents,
        source: VaultContents,
        now: Long,
    ): MergeResult {
        val here = normalise(destination.items, destination.deletions, clamp = false, now = now)
        // The file is the untrusted side, and the only one whose times are clamped.
        val there = normalise(source.items, source.deletions, clamp = true, now = now)

        val items = ArrayList<VaultItem>(here.size + there.size)
        val deletions = ArrayList<VaultBody.Deletion>()
        val added = ArrayList<VaultItem>()
        val replaced = ArrayList<VaultItem>()
        val removed = ArrayList<VaultItem>()
        var unchanged = 0

        for (id in here.keys + there.keys) {
            val mine = here[id]
            val theirs = there[id]
            // Strictly greater, so a tie leaves the destination exactly as it was.
            val winner = when {
                mine == null -> theirs
                theirs == null -> mine
                theirs.at > mine.at -> theirs
                else -> mine
            }
            when (winner) {
                is Side.Present -> {
                    items += winner.item
                    when {
                        mine == null -> added += winner.item
                        winner === theirs -> replaced += winner.item
                        else -> unchanged++
                    }
                }
                is Side.Deleted -> {
                    deletions += VaultBody.Deletion(id, winner.at)
                    // Only worth naming if it is here now. A deletion that agrees with one
                    // this vault already made removes nothing the owner can see.
                    if (mine is Side.Present) removed += mine.item
                }
                null -> Unit
            }
        }

        return MergeResult(
            contents = destination.with(items, deletions).adoptingUnknownMembersOf(source),
            preview = ImportPreview(
                added = added,
                replaced = replaced,
                removed = removed,
                unchanged = unchanged,
            ),
            kept = items.mapTo(HashSet()) { it.id },
        )
    }

    /**
     * Folds one side to exactly one outcome per identifier.
     *
     * A body may say an identifier is both present and deleted — a bug, an older writer, or
     * a file assembled by hand — and a merge that met one would have to guess. The same rule
     * that decides between the two sides decides here first, so by the time the sides meet
     * there are no contradictions left to special-case. Two items sharing an identifier are
     * folded the same way.
     */
    private fun normalise(
        items: List<VaultItem>,
        deletions: List<VaultBody.Deletion>,
        clamp: Boolean,
        now: Long,
    ): Map<ItemId, Side> {
        val out = HashMap<ItemId, Side>(items.size + deletions.size)
        for (item in items) {
            val settled = if (!clamp) item else item.copy(
                createdAt = minOf(item.createdAt, now),
                updatedAt = minOf(item.updatedAt, now),
            )
            val existing = out[settled.id]
            if (existing == null || settled.updatedAt > existing.at) {
                out[settled.id] = Side.Present(settled)
            }
        }
        for (deletion in deletions) {
            val at = if (clamp) minOf(deletion.deletedAt, now) else deletion.deletedAt
            val existing = out[deletion.id]
            if (existing == null || at > existing.at) out[deletion.id] = Side.Deleted(at)
        }
        return out
    }

    private sealed interface Side {
        val at: Long

        class Present(val item: VaultItem) : Side {
            override val at: Long get() = item.updatedAt
        }

        class Deleted(override val at: Long) : Side
    }
}

internal class MergeResult(
    val contents: VaultContents,
    val preview: ImportPreview,
    private val kept: Set<ItemId>,
) {
    /**
     * Whether an item survives, which is the only question the attachment step has.
     *
     * Note what it is *not* asking: whether the incoming version won. Superseding an item
     * does not change its [ItemId], so an attachment whose owner "lost" still has an owner —
     * only one of that owner's payloads lost. The attachment belongs to the entry, not to
     * the version of it that happened to be written last.
     */
    fun keeps(id: ItemId): Boolean = id in kept
}

/**
 * What an import would do, before it is allowed to do it.
 *
 * [removed] holds whole items rather than a count because removal is the only outcome that
 * destroys something the owner can currently see. There is no trash here and no undo, so the
 * entries about to go are named — a number would ask someone to agree to a deletion without
 * telling them what it is.
 */
class ImportPreview internal constructor(
    val added: List<VaultItem>,
    val replaced: List<VaultItem>,
    val removed: List<VaultItem>,
    val unchanged: Int,
) {
    /** Attachments the file carries that this vault does not have. Filled in by the session. */
    var attachmentsAdded: Int = 0
        internal set

    /** True when agreeing would change nothing — the common case of importing your own file. */
    val isEmpty: Boolean
        get() = added.isEmpty() && replaced.isEmpty() && removed.isEmpty() && attachmentsAdded == 0
}
