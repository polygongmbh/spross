package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The forms half of the "a drill must never accept one number for another" guard:
 * [TypoBridgeSweep] over the FORMS answer space, the cardinal half being
 * [TrainerTypoBridgeGuardTests].
 *
 * **Scoped to the forms space on purpose.** Two prompts can only be confused if a learner
 * can meet both in one run graded against one accepted set, and a run asks one task at a
 * time and grades it against that task's own readings. Sweeping form readings against plain
 * cardinals would therefore fail on "acht" ↔ "achte" for a confusion no run can produce:
 * the ordinal task accepts "achte", the cardinal task accepts "acht", and neither card ever
 * carries the other's answer. A mixed run changes nothing — it interleaves tasks, it does
 * not merge them.
 *
 * The enumeration swept is [NumberFormsAnswerSpace.drawableValues] — the same one the
 * production index reads, so a reading this guard can find is a reading that index holds.
 *
 * The allowlist is the first run's own offender list, audited entry by entry, exactly as the
 * cardinal one was built. Most of it is compounds of pairs already in
 * [TypoBridgeSweep.KNOWN_BRIDGES] — the sw 4/8 and uk 9/10 twins reappear behind hasi,
 * asilimia, mara and мінус, es sesenta/setenta behind menos and inside percentages — and the
 * rest is [FORM_BRIDGES]. One offender was NOT a twin and was fixed in the reading instead:
 * English hyphenated its article variants ("a-third"), which with hyphens deleted by the
 * comparison pipeline made the fraction 1/3 accept the ordinal's answer.
 */
class TrainerFormsTypoBridgeGuardTests {

    @Test
    fun germanFormsNeverBridge() {
        assertEquals(emptyList(), sweep("de"))
    }

    /** minus/negative, percent, per cent, times, and the one fraction ↔ ordinal pair. */
    @Test
    fun englishFormsBridgeOnlyTheKnownEightEightyPairs() {
        val known = sweep("en")
        assertEquals(6, known.size, "expected the six eight ↔ eighty pairs, got $known")
        assertTrue(known.all { "eight" in it && "eighty" in it }, "unexpected pair in $known")
    }

    @Test
    fun spanishFormsBridgeOnlyTheKnownSixtySeventyAndDecimoPairs() {
        val known = sweep("es")
        assertEquals(231, known.size, "expected 230 sesenta ↔ setenta pairs and un décimo")
        assertTrue("\"un décimo\" ↔ \"undécimo\"" in known, "the décimo pair vanished")
        val twins = known - "\"un décimo\" ↔ \"undécimo\""
        assertTrue(twins.all { "sesenta" in it && "setenta" in it }, "unexpected pair in $twins")
    }

    @Test
    fun italianFormsBridgeOnlyTheKnownTwentiethHundredthPairs() {
        val known = sweep("it")
        assertEquals(
            listOf("\"ventesimo\" ↔ \"centesimo\"", "\"ventesima\" ↔ \"centesima\""),
            known,
        )
    }

    @Test
    fun swahiliFormsBridgeOnlyTheKnownFourEightPairs() {
        val known = sweep("sw")
        assertEquals(882, known.size, "expected the nne ↔ nane pairs, got ${known.size}")
        assertTrue(known.all { "nne" in it && "nane" in it }, "unexpected pair in $known")
    }

    /** six/dix wearing the -ième the forms add, plus the one fraction ↔ ordinal pair. */
    @Test
    fun frenchFormsBridgeOnlyTheKnownSixTenPairs() {
        val known = sweep("fr")
        assertEquals(103, known.size, "expected the six ↔ dix pairs, got ${known.size}")
        assertTrue(known.all { "six" in it && "dix" in it }, "unexpected pair in $known")
    }

    @Test
    fun ukrainianFormsBridgeOnlyTheKnownNineTenPairs() {
        val known = sweep("uk")
        assertEquals(30, known.size, "expected the дев'ять ↔ десять pairs, got ${known.size}")
        assertTrue(
            known.all { "дев'ят" in it && "десят" in it },
            "unexpected pair in $known",
        )
    }

    /**
     * One substitution separates `ses` from `sep`, and Esperanto closes a numeral up into a
     * single word before every ending — so that one pair comes back inside every ordinal
     * (`sesa`, `dek-sesa`, `sesdek-kvina`), every `-ono` noun and every `-foje` adverb built
     * on six or seven. The family is DERIVED by that substitution instead of listed, because
     * the derivation IS the finding: forty literals would bury it, and a derived entry can
     * gate nothing else, since it differs from its twin in exactly those three letters.
     */
    @Test
    fun esperantoFormsBridgeOnlyTheKnownSixSevenPairs() {
        val known = sweep("eo", sixSevenTwins("eo"))
        assertEquals(976, known.size, "expected the ses ↔ sep family, got ${known.size}")
        assertTrue(known.all { "ses" in it && "sep" in it }, "unexpected pair in $known")
    }

    private fun sixSevenTwins(language: String): List<Set<String>> {
        val pack = trainerPacks.getValue(language)
        return NumberFormsAnswerSpace.drawableValues(pack.formLimits)
            .flatMap(pack::formReading)
            .flatMap { it.split(' ') }
            .map(TypoBridgeSweep::comparisonShape)
            .distinct()
            .flatMap { word ->
                word.indices
                    .filter { word.startsWith("ses", it) }
                    .map { at -> setOf(word, word.replaceRange(at, at + 3, "sep")) }
            }
            .distinct()
    }

    private fun sweep(language: String, extra: List<Set<String>> = emptyList()): List<String> {
        val pack = trainerPacks.getValue(language)
        val prompts = NumberFormsAnswerSpace.drawableValues(pack.formLimits)
            .map { TypoBridgeSweep.Prompt(pack.formReading(it)) }
            .filter { it.readings.isNotEmpty() }
        return TypoBridgeSweep.run(language, prompts, FORM_BRIDGES + extra)
    }

    private companion object {
        /**
         * The cardinal allowlist plus what the forms space adds, every entry audited:
         *
         * - **uk** — the дев'ять/десять twin wearing the endings forms give it. Ordinals
         *   (дев'ятий/десятий and their genders) and fraction denominators
         *   (одна дев'ята ↔ одна десята, сім дев'ятих ↔ сім десятих) stay one substitution
         *   apart once the comparison pipeline drops the apostrophe, exactly as the bare
         *   cardinals do.
         * - **en** — "eight ninths" ↔ "eighty ninth", the eight/eighty twin compounded with
         *   a fraction plural. It needs BOTH entries and bridges on neither alone.
         * - **es** — "un décimo" (1/10) ↔ "undécimo" (11.), the language's own space-only
         *   minimal pair, and the one entry a word-by-word comparison cannot express.
         * - **fr** — the six/dix twin wearing -ième, as a bare ordinal and welded inside the
         *   two compounds that carry it; and "six septièmes" (6/7) ↔ "dix-septième" (17.),
         *   the eight/eighty shape again — it needs the six/dix entry AND the septième one,
         *   and bridges on neither alone.
         * - **it** — the ventotto/centotto twin of the cardinals wearing the ordinal
         *   suffix: `venti` and `cento` differ in their first letter, and `-esimo` welds
         *   onto both the same way, in the masculine and in the feminine alike.
         */
        val FORM_BRIDGES = TypoBridgeSweep.KNOWN_BRIDGES + listOf(
            setOf("девята", "десята"),
            setOf("девяте", "десяте"),
            setOf("девятий", "десятий"),
            setOf("девятих", "десятих"),
            setOf("ninths", "ninth"),
            setOf("un décimo", "undécimo"),
            setOf("sixième", "dixième"),
            setOf("soixantesixième", "soixantedixième"),
            setOf("quatrevingtsixième", "quatrevingtdixième"),
            setOf("septièmes", "septième"),
            setOf("ventesimo", "centesimo"),
            setOf("ventesima", "centesima"),
        )
    }
}
