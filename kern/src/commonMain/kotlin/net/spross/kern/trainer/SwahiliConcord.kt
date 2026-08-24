package net.spross.kern.trainer

/**
 * Bantu noun-class concord on the Swahili cardinals.
 *
 * Six numerals — 1, 2, 3, 4, 5 and 8 — are Bantu stems that behave like adjectives and
 * agree with the class of the noun they count; 6, 7 and 9 (*sita*, *saba*, *tisa*) are
 * Arabic loans and never agree. The stems are BARE here on purpose:
 * [SwahiliNumbers.ones] is not a neutral citation form but the N-class (9/10) output,
 * where the nasal prefix mutates the stem of 2 (`N-` + `-wili` → *mbili*). Every other
 * class has to be built from `-wili`, never from *mbili*.
 *
 * **Only the trailing ones-word agrees.** Concord applies whenever one of the six is used
 * in the single-digit column, "even if part of a larger number" (Almasi p. 187) — and
 * nowhere else in the reading, so a numeral multiplying *mia*, *elfu* or *milioni* stays bare
 * because it agrees with those, not with the counted noun. The singular/plural choice
 * follows that final digit's own value, not the total: 101 books is
 * *vitabu mia moja na **ki**moja* with the SINGULAR prefix, while 98 cars is
 * *magari tisini na **ma**nane*; against them *mabegi ya plastiki mia nane bilioni* keeps
 * *nane* bare in multiplier position.
 *
 * Readings past 9999 are left unconcorded on purpose: the sources disagree about where
 * agreement lands once *elfu* takes a compound multiplier (*elfu kumi na nne*), and this
 * app's bar is sourced or not shipped. Nothing counted in a sentence frame gets that big.
 *
 * Sources: Almasi, Fallon & Wared, *Swahili Grammar for Introductory and Intermediate
 * Levels* (UPA 2014), ch. 18 "Numbers" — Table 18.1 (p. 188) for the prefixes, p. 187 for
 * the stem rule and the single-digit-column rule; corroborated per stem by Wiktionary's
 * declension tables. The full citations live in `docs/number-forms.md` §Swahili.
 */
object SwahiliConcord {

    /**
     * The noun classes a shipped frame counts in. Only classes an authored frame needs
     * are listed — the app teaches the contrast, it does not tabulate Bantu grammar.
     * N-class (9/10) is deliberately absent: its numerals are what [SwahiliNumbers]
     * already spells, so a frame counting *sahani* or *funguo* carries no class at all.
     */
    enum class NounClass { KI_VI, JI_MA }

    /** The inflecting numerals as bare stems; 6/7/9 are absent because they never agree. */
    private val stems = mapOf(1 to "moja", 2 to "wili", 3 to "tatu", 4 to "nne", 5 to "tano", 8 to "nane")

    /**
     * The agreement prefix per class, singular then plural — Almasi's Table 18.1 is written
     * on exactly that split: JI-/MA- takes no prefix in the singular (*jicho moja*) and
     * `ma-` in the plural (*macho mawili*); KI-/VI- takes `ki-` (*kiti kimoja*) and `vi-`
     * (*viti viwili*).
     */
    private val prefixes = mapOf(
        NounClass.KI_VI to ("ki" to "vi"),
        NounClass.JI_MA to ("" to "ma"),
    )

    /** Past this the sources stop agreeing on where concord lands; see the class doc. */
    private const val CONCORD_CEILING = 9999L

    /**
     * [n] read out with [nounClass]'s concord on its trailing ones-word.
     *
     * The prefix concatenates onto the stem unchanged — "the numbers 1, 3, 4, 5 and 8
     * simply have the prefix attached without any modifications" (Almasi p. 187), and 2 is
     * the one that first drops back to `-wili`. The geminate survives (`vi-` + `-nne` →
     * *vinne*), and the palatalized `vy-`/`j-` allomorphs never apply because every numeral
     * stem is consonant-initial.
     *
     * Everything whose final digit is 0, 6, 7 or 9 comes back exactly as [SwahiliNumbers]
     * spells it — that one test covers the Arabic loans and every round ten, hundred and
     * thousand at once, which is why there is no separate rule for them.
     */
    fun cardinal(n: Long, nounClass: NounClass): String {
        val reading = SwahiliNumbers.cardinal(n)
        if (n !in 1..CONCORD_CEILING) return reading
        val stem = stems[(n % 10).toInt()] ?: return reading
        val (singular, plural) = prefixes.getValue(nounClass)
        val concorded = (if (n % 10 == 1L) singular else plural) + stem
        // The reading's LAST word is the ones-column one whenever the final digit is not
        // zero, so replacing it leaves any earlier multiplier ("mia nane na …") bare —
        // which is the sourced rule, not a coincidence of the string surgery.
        return (reading.split(" ").dropLast(1) + concorded).joinToString(" ")
    }

    /**
     * The accepted spellings of [n] beside [nounClass]'s noun: the concorded reading, plus
     * the `na`-less one speakers routinely use, exactly as [SwahiliNumbers.acceptedVariants]
     * offers it for the plain drill — dropping the connectors is orthogonal to agreement.
     *
     * An UNCONCORDED *viti nne* is never among them: it is the exact error a class-marked
     * frame exists to train (the rule [PhraseSlots] applies to feminine Ukrainian numerals,
     * from the other side).
     */
    fun acceptedVariants(n: Long, nounClass: NounClass): List<String> {
        val canonical = cardinal(n, nounClass)
        val naless = canonical.replace(" na ", " ")
        return if (naless == canonical) listOf(canonical) else listOf(canonical, naless)
    }
}
