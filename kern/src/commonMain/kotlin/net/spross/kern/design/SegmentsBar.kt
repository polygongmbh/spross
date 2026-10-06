package net.spross.kern.design

import kotlin.math.max
import kotlin.math.min

/**
 * The session progress bar's slots: one segment per answer, then one undivided stretch for the rest.
 * The platform colors each of the [shown] latest answers and parts the slots by [gap] points (dp).
 *
 * An endless run keeps answering past any fixed total,
 * so the bar windows to its latest answers instead of one sliver per answer forever.
 */
class SegmentsBar(answered: Int, remaining: Int) {
    /** How many of the latest answers the bar draws; the platform takes that many off the end of its list. */
    val shown: Int = min(max(answered, 0), WINDOW)

    /** Where the drawn answers start in the run's full list — a stable identity for each segment. */
    val firstShown: Int = max(answered, 0) - shown

    val remaining: Int = max(remaining, 0)

    val slots: Int = shown + this.remaining

    /** The parting between two slots: a hairline, thinner once that many slots crowd the bar. */
    val gap: Double = if (slots > WINDOW) CROWDED_GAP else GAP

    companion object {
        /** The most answers the bar draws at once. */
        const val WINDOW = 40
        private const val GAP = 1.0
        private const val CROWDED_GAP = 0.5
    }
}
