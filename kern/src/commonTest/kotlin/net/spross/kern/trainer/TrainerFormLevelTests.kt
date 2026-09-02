package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Forms GENERATOR: which values each Sprosse draws, how the prompt is written, and
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
        assertEquals(listOf("de", "en", "es", "sw", "uk", "eo", "fr", "it"), authored)
        // The gate still has to close: a language with no pack at all offers no Forms drill.
        assertFalse(Trainer.supportsForms("pt"))
    }

    @Test
    fun eachOfTheFirstSixSprossenAddsOneFormAndKeepsTheOnesBelow() {
        for (level in 1..6) {
            assertEquals(NumberForm.entries.take(level).toSet(), sprosseForms(level))
        }
        for (level in 7..Trainer.maxLevel(TrainerKind.Forms)) {
            assertEquals(NumberForm.entries.toSet(), sprosseForms(level), "Sprosse $level widens, adds nothing")
        }
    }

    @Test
    fun theFirstSprosseDrawsNothingButSmallNegatives() {
        for (language in authored) {
            for (value in draws(language, 1)) {
                val negative = value as? NumberValue.Negative
                assertTrue(negative != null, "$language Sprosse 1 drew $value")
                assertTrue(negative.magnitude in 1..20, "$language Sprosse 1 drew $negative")
            }
        }
    }

    // The Mix magnitude

    /**
     * Under Mix a form is sized by the Numbers Sprosse, so Sprosse 1's "−7" becomes a
     * seven-digit negative and a decimal grows a five-digit whole part — while the
     * forms on offer, and the reading that grades them, stay the Sprosse's own.
     */
    @Test
    fun theMixMagnitudeWidensTheFormsThatHaveOne() {
        val rng = Random(0x111)
        val limits = FormLimits(forms = setOf(NumberForm.Negative, NumberForm.Decimal))
        val wide = List(200) { checkNotNull(drawForm(limits, level = 1, rng = rng, magnitudeDigits = 7)) }
        for (value in wide) {
            val magnitude = when (value) {
                is NumberValue.Negative -> value.magnitude
                is NumberValue.Decimal -> value.whole
                else -> error("Sprosse 1 with two forms drew $value")
            }
            assertTrue(magnitude >= 1_000_000, "seven digits asked for, got $value")
        }
        // Sprosse 1 still offers Sprosse 1: no ordinal, no percentage, however wide the value.
        assertTrue(wide.any { it is NumberValue.Negative })
    }

    @Test
    fun theFormsWithoutAMagnitudeIgnoreTheMixWidening() {
        val rng = Random(0x222)
        val limits = FormLimits(forms = setOf(NumberForm.Percent, NumberForm.Fraction))
        for (value in List(200) { checkNotNull(drawForm(limits, level = 10, rng = rng, magnitudeDigits = 10)) }) {
            when (value) {
                is NumberValue.Percent -> assertTrue(value.n in 1..100, "$value")
                is NumberValue.Fraction -> assertTrue(value.denominator <= 12, "$value")
                else -> error("unexpected $value")
            }
        }
    }

    /** Zero magnitude is the plain ladder, byte for byte — Mix off changes nothing. */
    @Test
    fun noMixMagnitudeLeavesTheLadderExactlyAsItWas() {
        for (language in authored) {
            assertEquals(
                Trainer.sample(TrainerKind.Forms, language, 6, Random(11)),
                Trainer.sampleForms(language, level = 6, magnitudeDigits = 0, rng = Random(11)),
            )
        }
    }

    @Test
    fun everySprosseStaysInsideTheLanguagesOwnLimits() {
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

    /** Below Sprosse 8 a fraction is a unit fraction, and below Sprosse 9 an ordinal stays small. */
    @Test
    fun theGentleSprossenStayGentle() {
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
     * A Sprosse offering nothing this language reads still has to draw: the pack's own set
     * stands in. Sprosse 1 is negatives, so a fractions-only language draws a fraction there.
     */
    @Test
    fun aSprosseWithNothingToOfferFallsBackToTheLanguagesOwnForms() {
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
        assertEquals(',', Trainer.pack("eo").decimalMark)
        assertEquals(',', Trainer.pack("fr").decimalMark)
        assertEquals(',', Trainer.pack("it").decimalMark)
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
