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
 * The enumeration is bounded to stay the order of the cardinal sweep's German 0–999:
 * negatives 0–99, decimals with a whole part 0–9 and one or two fraction digits, percent and
 * multiplicatives over the range the ladder draws (1–100), every reduced fraction the pack
 * allows, and ordinals over the pack's own range. Values the ladder can never draw are left
 * out — an all-zero fraction-digit string is repaired at the source
 * ([NumberFormLadder]), so no reading exists for "3,0" to bridge with.
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

    @Test
    fun ukrainianFormsBridgeOnlyTheKnownNineTenPairs() {
        val known = sweep("uk")
        assertEquals(30, known.size, "expected the дев'ять ↔ десять pairs, got ${known.size}")
        assertTrue(
            known.all { "дев'ят" in it && "десят" in it },
            "unexpected pair in $known",
        )
    }

    private fun sweep(language: String): List<String> {
        val pack = trainerPacks.getValue(language)
        val prompts = drawableValues(pack.formLimits)
            .map { TypoBridgeSweep.Prompt(pack.formReading(it)) }
            .filter { it.readings.isNotEmpty() }
        return TypoBridgeSweep.run(language, prompts, FORM_BRIDGES)
    }

    /** Every value the bounded enumeration offers, for the forms this pack reads. */
    private fun drawableValues(limits: FormLimits): List<NumberValue> = buildList {
        if (NumberForm.Negative in limits.forms) {
            for (magnitude in 0L..99L) add(NumberValue.Negative(magnitude))
        }
        if (NumberForm.Decimal in limits.forms) {
            for (whole in 0L..9L) for (digits in FRACTION_DIGITS) add(NumberValue.Decimal(whole, digits))
        }
        if (NumberForm.Percent in limits.forms) {
            for (n in 1L..100L) add(NumberValue.Percent(n))
        }
        if (NumberForm.Multiplicative in limits.forms) {
            for (n in 1L..100L) add(NumberValue.Multiplicative(n))
        }
        if (NumberForm.Fraction in limits.forms) {
            for (d in limits.fractionDenominators.filter { it in 2..12 }.sorted()) {
                for (n in 1 until d) {
                    if (gcd(n, d) == 1) add(NumberValue.Fraction(n.toLong(), d.toLong()))
                }
            }
        }
        if (NumberForm.Ordinal in limits.forms) {
            for (n in maxOf(1L, limits.ordinalRange.first)..limits.ordinalRange.last) {
                add(NumberValue.Ordinal(n))
            }
        }
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

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
            setOf("ventesimo", "centesimo"),
            setOf("ventesima", "centesima"),
        )

        /** One or two digits, never all zeros — the ladder repairs that draw. */
        val FRACTION_DIGITS: List<String> =
            (1..9).map { it.toString() } +
                (0..99).map { it.toString().padStart(2, '0') }.filter { it != "00" }
    }
}
