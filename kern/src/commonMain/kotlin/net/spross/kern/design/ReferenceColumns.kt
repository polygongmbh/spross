package net.spross.kern.design

import net.spross.kern.trainer.ReferenceEntry
import net.spross.kern.trainer.letters

/**
 * How many columns a reference band stands in: two for a band of short readings, one for everything else.
 * The counting words are read at a glance,
 * and a page that spends a whole line on "vier" is a page of scrolling.
 *
 * Two tests, because either alone gets it wrong.
 * The letters are counted rather than measured:
 * a pair picked off a measurement is picked narrower than it renders,
 * and "dreizehn" then wraps inside its column.
 * But a count knows nothing of how wide the page is,
 * so a panel narrower than [PAIRED_MIN_WIDTH] keeps the single column whatever its readings weigh.
 * Larger text keeps it too, where a reading has the whole width to grow into.
 */
object ReferenceColumns {
    /**
     * The narrowest panel a pair is drawn in, inside its padding, in points.
     * A 360-point phone leaves 296 there and a 320-point one only 256,
     * where a half-width column is under 110 and even a ten-letter row wraps mid-word.
     */
    const val PAIRED_MIN_WIDTH: Double = 288.0

    /** The widest row a phone fits twice, in letters, value and reading together. */
    private const val PAIRED_ROW_LETTERS = 10

    /** Fewer rows than this read fine in one column whatever their width. */
    private const val PAIRED_MIN_ROWS = 6

    /**
     * [largeText]: the learner's text is set above the default size.
     * [width]: the panel's inner width, where the app knows it before laying the band out.
     */
    fun count(entries: List<ReferenceEntry>, largeText: Boolean, width: Double = Double.POSITIVE_INFINITY): Int {
        if (largeText || width < PAIRED_MIN_WIDTH || entries.size < PAIRED_MIN_ROWS) return 1
        val widest = entries.maxOf { it.value.letters().size + it.reading.letters().size }
        return if (widest <= PAIRED_ROW_LETTERS) 2 else 1
    }
}
