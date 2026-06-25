package com.bridgerwatch.ballistics.transfer

/**
 * Which of the Bridger Watch's four ballistic-profile slots to target.
 *
 * The watch holds up to four profiles concurrently. Pin a specific slot to
 * overwrite it, or use [AUTO] to let Bridger decide (it defaults to slot 0,
 * or prompts the user when all four are occupied).
 */
enum class BridgerSlot(val index: Int?) {
    /** Don't pin a slot. Bridger defaults to slot 0 (and names the bytes `bdata0.json`). */
    AUTO(null),
    SLOT0(0),
    SLOT1(1),
    SLOT2(2),
    SLOT3(3);

    companion object {
        /** Creates a pinned slot from an index in `0..3`, or `null` if out of range. */
        fun fromIndex(index: Int): BridgerSlot? =
            entries.firstOrNull { it != AUTO && it.index == index }
    }
}
