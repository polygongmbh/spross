package net.spross.kern.trainer

/**
 * What a Sprosse circle says about a ladder's record: never stood on, stood on by some run
 * ([Reached]), or answered out by one ([Cleared]) — the last only where the Sprosse enumerates.
 * Answered out beats stood on, so a Sprosse cleared reads as cleared
 * even where the best reached is filed below it.
 */
enum class SprosseMark {
    Untouched, Reached, Cleared;

    companion object {
        fun of(cleared: Boolean, reached: Boolean): SprosseMark = when {
            cleared -> Cleared
            reached -> Reached
            else -> Untouched
        }

        /** One Sprosse of a ladder that files its [cleared] Sprossen and its [bestSprosse]. */
        fun of(sprosse: Int, cleared: Set<Int>, bestSprosse: Int): SprosseMark =
            of(sprosse in cleared, sprosse <= bestSprosse)
    }
}
