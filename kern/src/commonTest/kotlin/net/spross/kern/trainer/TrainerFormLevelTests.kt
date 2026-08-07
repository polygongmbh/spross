package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Forms GENERATOR: which values each rung draws, how the prompt is written, and
 * what the reversed task takes back. The readings themselves are pinned separately
 * ([TrainerFormsTests]) — this file never asserts a word.
 */
class TrainerFormLevelTests {

    private val authored = Trainer.languages.filter(Trainer::supportsForms)

    private fun limits(language: String) = Trainer.pack(language).formLimits

    private fun draws(language: String, level: Int, count: Int = 200): List<NumberValue> {
        val rng = Random(0x5EED + level)
        return List(count) { checkNotNull(drawForm(limits(language), level, rng)) }
    }

    // The ladder

    @Test
    fun theFormsChipIsOfferedExactlyWhereAPackAuthorsForms() {
        assertEquals(listOf("de", "en", "es", "sw", "uk"), authored)
        // The gate still has to close: a language with no pack at all offers no Forms drill.
        assertFalse(Trainer.supportsForms("fr"))
    }

    @Test
    fun eachOfTheFirstSixRungsAddsOneFormAndKeepsTheOnesBelow() {
        for (level in 1..6) {
            assertEquals(NumberForm.entries.take(level).toSet(), rungForms(level))
        }
        for (level in 7..Trainer.maxLevel(TrainerKind.Forms)) {
            assertEquals(NumberForm.entries.toSet(), rungForms(level), "rung $level widens, adds nothing")
        }
    }

    @Test
    fun theFirstRungDrawsNothingButSmallNegatives() {
        for (language in authored) {
            for (value in draws(language, 1)) {
                val negative = value as? NumberValue.Negative
                assertTrue(negative != null, "$language rung 1 drew $value")
                assertTrue(negative.magnitude in 1..20, "$language rung 1 drew $negative")
            }
        }
    }

    @Test
    fun everyRungStaysInsideTheLanguagesOwnLimits() {
        for (language in authored) {
            val limits = limits(language)
            for (level in 1..Trainer.maxLevel(TrainerKind.Forms)) {
                for (value in draws(language, level)) {
                    assertTrue(value.form in limits.forms, "$language level $level drew ${value.form}")
                    when (value) {
                        is NumberValue.Fraction -> assertReduced(value, limits, "$language level $level")
                        is NumberValue.Ordinal ->
                            assertTrue(value.n in limits.ordinalRange, "$language level $level: $value")
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun assertReduced(value: NumberValue.Fraction, limits: FormLimits, where: String) {
        assertTrue(value.denominator.toInt() in limits.fractionDenominators, "$where: $value")
        assertTrue(value.denominator <= 12, "$where: $value")
        assertTrue(value.numerator in 1 until value.denominator, "$where: $value")
        assertEquals(1L, gcd(value.numerator, value.denominator), "$where: $value is not reduced")
    }

    /**
     * A trailing zero is a real reading ("3,40" ≠ "3,4"), but an all-zero fractional part
     * is degenerate wherever the place is named ("три цілих нуль сотих").
     */
    @Test
    fun aDrawnDecimalNeverHasAnAllZeroFractionalPart() {
        for (language in authored) {
            for (level in 1..Trainer.maxLevel(TrainerKind.Forms)) {
                for (value in draws(language, level)) {
                    if (value is NumberValue.Decimal) {
                        assertTrue(
                            value.fractionDigits.any { it != '0' },
                            "$language level $level drew $value",
                        )
                    }
                }
            }
        }
    }

    /** Below rung 8 a fraction is a unit fraction, and below rung 9 an ordinal stays small. */
    @Test
    fun theGentleRungsStayGentle() {
        for (language in authored) {
            for (level in 1..7) {
                for (value in draws(language, level)) {
                    if (value is NumberValue.Fraction) assertEquals(1L, value.numerator, "level $level")
                }
            }
            for (level in 1..8) {
                for (value in draws(language, level)) {
                    if (value is NumberValue.Ordinal) assertTrue(value.n <= 12, "level $level: $value")
                }
            }
        }
    }

    /**
     * A rung offering nothing this language reads still has to draw: the pack's own set
     * stands in. Rung 1 is negatives, so a fractions-only language draws a fraction there.
     */
    @Test
    fun aRungWithNothingToOfferFallsBackToTheLanguagesOwnForms() {
        val fractionsOnly = FormLimits(forms = setOf(NumberForm.Fraction))
        val value = drawForm(fractionsOnly, level = 1, rng = Random(7))
        assertTrue(value is NumberValue.Fraction, "expected the pack's own form, got $value")
    }

    @Test
    fun aLanguageThatReadsNoFormDrawsNothing() {
        assertNull(drawForm(FormLimits(), level = 5, rng = Random(7)))
    }

    @Test
    fun aFormWhoseParameterSpaceIsEmptyIsNeverDrawn() {
        val noDenominators = FormLimits(
            forms = setOf(NumberForm.Fraction, NumberForm.Ordinal),
            fractionDenominators = emptySet(),
        )
        val rng = Random(11)
        repeat(50) { assertTrue(drawForm(noDenominators, 10, rng) is NumberValue.Ordinal) }
    }

    // The prompt

    @Test
    fun theWrittenFormIsNeutralExceptForTheDecimalMark() {
        assertEquals("-45", renderForm(NumberValue.Negative(45), ',', grouped = false))
        assertEquals("3,7", renderForm(NumberValue.Decimal(3, "7"), ',', grouped = false))
        assertEquals("3.7", renderForm(NumberValue.Decimal(3, "7"), '.', grouped = false))
        assertEquals("45\u202F%", renderForm(NumberValue.Percent(45), ',', grouped = false))
        assertEquals("3\u00D7", renderForm(NumberValue.Multiplicative(3), ',', grouped = false))
        assertEquals("3/4", renderForm(NumberValue.Fraction(3, 4), ',', grouped = false))
        // The ordinal mark is the same in every language, like the group separator.
        assertEquals("20.", renderForm(NumberValue.Ordinal(20), ',', grouped = false))
        assertEquals("20.", renderForm(NumberValue.Ordinal(20), '.', grouped = false))
    }

    @Test
    fun groupingReachesTheIntegerPartOnly() {
        assertEquals("-12\u202F345", renderForm(NumberValue.Negative(12345), ',', grouped = true))
        assertEquals("999,1234", renderForm(NumberValue.Decimal(999, "1234"), ',', grouped = true))
    }

    /**
     * The mark is a claim about the language, not a fallback: East African maths writes
     * 0.01 (TIE's Hisabati series), so Swahili takes the point deliberately.
     */
    @Test
    fun eachPackWritesItsOwnDecimalMark() {
        assertEquals(',', Trainer.pack("de").decimalMark)
        assertEquals('.', Trainer.pack("en").decimalMark)
        assertEquals(',', Trainer.pack("es").decimalMark)
        assertEquals('.', Trainer.pack("sw").decimalMark)
        assertEquals(',', Trainer.pack("uk").decimalMark)
    }

    // Sampling

    @Test
    fun everySampledFormTaskIsWellFormed() {
        val rng = Random(0xF04D)
        for (language in Trainer.languages) {
            for (level in 1..Trainer.maxLevel(TrainerKind.Forms)) {
                repeat(50) {
                    val task = Trainer.sample(TrainerKind.Forms, language, level, rng)
                    val where = "$language level $level ${task.prompt}"
                    assertEquals(TrainerKind.Forms, task.kind, where)
                    assertEquals(language, task.language, where)
                    assertTrue(task.prompt.isNotEmpty(), where)
                    assertTrue(task.accepted.all { it.isNotEmpty() }, where)
                    assertTrue(task.display in task.accepted, "$where: ${task.display}")
                }
            }
        }
    }

    @Test
    fun levelsClampInsteadOfThrowing() {
        for (language in authored) {
            assertEquals(
                Trainer.sample(TrainerKind.Forms, language, 1, Random(3)),
                Trainer.sample(TrainerKind.Forms, language, -3, Random(3)),
            )
            assertEquals(
                Trainer.sample(TrainerKind.Forms, language, 10, Random(3)),
                Trainer.sample(TrainerKind.Forms, language, 99, Random(3)),
            )
        }
    }

    @Test
    fun samplingIsDeterministicForSeededGenerator() {
        val a = Random(0xC0FFEE)
        val b = Random(0xC0FFEE)
        repeat(100) {
            assertEquals(
                Trainer.sample(TrainerKind.Forms, "de", a),
                Trainer.sample(TrainerKind.Forms, "de", b),
            )
        }
    }

    // Reverse

    private fun formTask(language: String, prompt: String, display: String = prompt): TrainerTask =
        TrainerTask(TrainerKind.Forms, language, prompt, listOf("reading"), "reading", promptDisplay = display)

    @Test
    fun reversedFormsTakeTheNotationTheDrillDidNotAskAbout() {
        assertEquals(listOf("3,7", "3.7"), Trainer.reversed(formTask("de", "3,7")).accepted)
        assertEquals(listOf("3.7", "3,7"), Trainer.reversed(formTask("en", "3.7")).accepted)
        assertEquals(listOf("20.", "20"), Trainer.reversed(formTask("de", "20.")).accepted)
        assertEquals(
            listOf("45\u202F%", "45%", "45"),
            Trainer.reversed(formTask("de", "45\u202F%")).accepted,
        )
        assertEquals(
            listOf("3\u00D7", "3x", "3"),
            Trainer.reversed(formTask("de", "3\u00D7")).accepted,
        )
        assertEquals(listOf("-45"), Trainer.reversed(formTask("de", "-45")).accepted)
    }

    @Test
    fun aReversedFormRevealsTheGroupedWriting() {
        val back = Trainer.reversed(formTask("de", "-12345", "-12\u202F345"))
        assertEquals("-12\u202F345", back.display)
        assertEquals(listOf("-12\u202F345", "-12345"), back.accepted)
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
}
