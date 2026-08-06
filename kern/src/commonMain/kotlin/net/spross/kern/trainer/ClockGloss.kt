package net.spross.kern.trainer

/**
 * Which readings are worth naming on a clock reveal.
 *
 * A gloss has room for two or three lines, and they are spent on the OTHER WAYS to say
 * the time — "Viertel vor sieben" against "Dreiviertel sieben", "menos cuarto" against
 * "para las". A reading that is the same one with a word added or dropped (the part of
 * the day, an article, "Minuten", "хвилин", the "y") teaches nothing the display did
 * not: it is not an alternative, it is the same alternative said shorter.
 *
 * So a candidate is dropped when its words are a subsequence of the display's, or the
 * display's are a subsequence of its — and again against every alternative already
 * picked, so two spellings of one idiom never take both slots.
 *
 * What this rule cannot see is a joiner swap: "quarter till five", "quarter of five" and
 * "quarter to five" are one construction with the preposition changed. Which joiners are
 * interchangeable is a language's own rule, so a generator that has such pairs builds its
 * candidate list explicitly (EnglishClock) rather than filtering its accepted set.
 */
internal object ClockGloss {

    /** Private on purpose: [line] is the one entry point, and it owns the empty-gloss rule. */
    private fun alternatives(display: String, candidates: List<String>, limit: Int): List<String> {
        val picked = mutableListOf<String>()
        for (candidate in candidates) {
            if (picked.size == limit) break
            if (candidate == display || sameIdiom(candidate, display)) continue
            if (picked.any { sameIdiom(candidate, it) }) continue
            picked += candidate
        }
        return picked
    }

    /**
     * The reveal's line: the picks under the language's own lead-in, or nothing at all —
     * a gloss with no alternative left to name is not a shorter gloss, it is no gloss.
     * [lead] and [separator] are words in the language being ANSWERED in and stay at the
     * call site; centralizing them would make five user-visible strings one edit apart.
     */
    fun line(
        display: String,
        candidates: List<String>,
        limit: Int,
        lead: String,
        separator: String,
    ): String? {
        val picks = alternatives(display, candidates, limit)
        return if (picks.isEmpty()) null else lead + picks.joinToString(separator)
    }

    /** True when one reading is the other with whole words dropped. */
    private fun sameIdiom(a: String, b: String): Boolean {
        val left = words(a)
        val right = words(b)
        return contains(left, right) || contains(right, left)
    }

    /** Is [inner] what is left of [outer] after dropping whole words? */
    private fun contains(outer: List<String>, inner: List<String>): Boolean {
        var next = 0
        for (word in outer) if (next < inner.size && word == inner[next]) next++
        return next == inner.size
    }

    private fun words(reading: String): List<String> =
        reading.lowercase().split(' ').filter { it.isNotEmpty() }
}
