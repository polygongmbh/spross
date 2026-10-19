package net.spross.kern.design

/** How the hub card's drill chips break into lines. */
object ChipRows {
    /**
     * How many chips stand on each line, top first, for [count] chips.
     * Three or fewer share one line; past that the card breaks into two,
     * the odd chip on top so the card narrows as it is read — 4 stand 2+2, 5 stand 3+2, 7 stand 4+3.
     * A line overflows rather than wrapping, so the break is drawn, never discovered.
     */
    fun of(count: Int): List<Int> = when {
        count <= 0 -> emptyList()
        count <= 3 -> listOf(count)
        else -> listOf((count + 1) / 2, count / 2)
    }
}
