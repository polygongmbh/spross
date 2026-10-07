package net.spross.kern.trainer

/**
 * The seven entries free practice is made of, in the order the hub offers them.
 *
 * The ONE place the roster is written down. The hub's chips, their faces, what each entry
 * gates on and the string key that names it all derive from this list, so an eighth drill is
 * one entry here and a compiler error at every place that has to answer for it.
 *
 * It names the entries and the face each wears ([emoji]); a title, a route and a layout are the
 * platform's, and a drill's own machinery stands under its own topic prefix
 * (`CountryDrill`, `WordScrambleRun`; `docs/drills.md`).
 */
enum class Drill(val emoji: String) {
    Letters("🔤"),
    Numbers(numbersReadingEmoji(NumbersReading.Cardinal)),
    Dates("📅"),
    Countries("🌍"),
    SentenceScramble("🧩"),
    WordScramble("🔀"),
    Opposites("↔️"),
    ;

    companion object {
        /**
         * How many chips stand on each line of the hub card, top first, for [count] chips.
         * Three or fewer share one line; past that the card breaks into two,
         * the odd chip on top so the card narrows as it is read — 4 stand 2+2, 5 stand 3+2, 7 stand 4+3.
         * A line overflows rather than wrapping, so the break is drawn, never discovered.
         */
        fun chipRows(count: Int): List<Int> = when {
            count <= 0 -> emptyList()
            count <= 3 -> listOf(count)
            else -> listOf((count + 1) / 2, count / 2)
        }
    }
}
