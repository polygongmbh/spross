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
 * (`Numbers<Language>FormsTests`, one file per language) — this file never asserts a word.
 */
class NumbersFormSprosseTests {

    private val authored = Numbers.languages.filter(Numbers::supportsForms)

    private fun limits(language: String) = Numbers.pack(language).formLimits

    private fun draws(language: String, sprosse: Int, count: Int = 200): List<NumberValue> {
        val rng = Random(0x5EED + sprosse)
        return List(count) { checkNotNull(drawForm(limits(language), sprosse, rng)) }
    }

    // The ladder

    @Test
    fun theFormsChipIsOfferedExactlyWhereAPackAuthorsForms() {
        assertEquals(listOf("de", "en", "es", "sw", "uk", "eo", "fr", "it"), authored)
        // The gate still has to close: a language with no pack at all offers no Forms drill.
        assertFalse(Numbers.supportsForms("pt"))
    }

    @Test
    fun eachOfTheFirstSevenSprossenAddsOneFormAndKeepsTheOnesBelow() {
        for (sprosse in 1..7) {
            assertEquals(NumberForm.entries.take(sprosse).toSet(), sprosseForms(sprosse))
        }
        for (sprosse in 8..Numbers.maxSprosse(NumbersReading.Form)) {
            assertEquals(NumberForm.entries.toSet(), sprosseForms(sprosse), "Sprosse $sprosse widens, adds nothing")
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
        val wide = List(200) { checkNotNull(drawForm(limits, sprosse = 1, rng = rng, magnitudeDigits = 7)) }
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
        for (value in List(200) { checkNotNull(drawForm(limits, sprosse = 10, rng = rng, magnitudeDigits = 10)) }) {
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
                Numbers.sample(NumbersReading.Form, language, 6, Random(11)),
                Numbers.sampleForms(language, sprosse = 6, magnitudeDigits = 0, rng = Random(11)),
            )
        }
    }

    @Test
    fun everySprosseStaysInsideTheLanguagesOwnLimits() {
        for (language in authored) {
            val limits = limits(language)
            for (sprosse in 1..Numbers.maxSprosse(NumbersReading.Form)) {
                for (value in draws(language, sprosse)) {
                    assertTrue(value.form in limits.forms, "$language Sprosse $sprosse drew ${value.form}")
                    when (value) {
                        is NumberValue.Fraction -> assertReduced(value, limits, "$language Sprosse $sprosse")
                        is NumberValue.Ordinal ->
                            assertTrue(value.n in limits.ordinalRange, "$language Sprosse $sprosse: $value")
                        is NumberValue.Price -> assertPriced(value, limits, "$language Sprosse $sprosse")
                        else -> Unit
                    }
                }
            }
        }
    }

    /** A price is drawn in the pack's own currency, on its step, and is never nothing at all. */
    private fun assertPriced(value: NumberValue.Price, limits: FormLimits, where: String) {
        assertEquals(limits.currency, value.currency, where)
        assertEquals(0L, value.units % value.currency.step, "$where: $value")
        assertTrue(value.cents in 0..99, "$where: $value")
        if (!value.currency.minorUnit) assertEquals(0L, value.cents, "$where: $value")
        assertTrue(value.units + value.cents > 0, "$where: $value")
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
            for (sprosse in 1..Numbers.maxSprosse(NumbersReading.Form)) {
                for (value in draws(language, sprosse)) {
                    if (value is NumberValue.Decimal) {
                        assertTrue(
                            value.fractionDigits.any { it != '0' },
                            "$language Sprosse $sprosse drew $value",
                        )
                    }
                }
            }
        }
    }

    /**
     * Below Sprosse 8 a fraction is a unit fraction, below Sprosse 9 an ordinal stays small,
     * and below Sprosse 10 a price stays a shop's: at most twenty of the currency's steps.
     */
    @Test
    fun theGentleSprossenStayGentle() {
        for (language in authored) {
            for (sprosse in 1..7) {
                for (value in draws(language, sprosse)) {
                    if (value is NumberValue.Fraction) assertEquals(1L, value.numerator, "Sprosse $sprosse")
                }
            }
            for (sprosse in 1..8) {
                for (value in draws(language, sprosse)) {
                    if (value is NumberValue.Ordinal) assertTrue(value.n <= 12, "Sprosse $sprosse: $value")
                }
            }
            for (sprosse in 1..9) {
                for (value in draws(language, sprosse)) {
                    if (value is NumberValue.Price) {
                        assertTrue(value.units in 1..20 * value.currency.step, "Sprosse $sprosse: $value")
                    }
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
        val value = drawForm(fractionsOnly, sprosse = 1, rng = Random(7))
        assertTrue(value is NumberValue.Fraction, "expected the pack's own form, got $value")
    }

    @Test
    fun aLanguageThatReadsNoFormDrawsNothing() {
        assertNull(drawForm(FormLimits(), sprosse = 5, rng = Random(7)))
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

    /** A price wears its currency's tag, and a whole amount shows no cents. */
    @Test
    fun aPriceIsWrittenTheWayItsTagIs() {
        assertEquals("3,50\u00A0€", renderForm(NumberValue.Price(3, 50, Currency.Euro), ',', grouped = false))
        assertEquals("0,05\u00A0€", renderForm(NumberValue.Price(0, 5, Currency.Euro), ',', grouped = false))
        assertEquals("3\u00A0€", renderForm(NumberValue.Price(3, 0, Currency.Euro), ',', grouped = false))
        assertEquals("$3.50", renderForm(NumberValue.Price(3, 50, Currency.Dollar), '.', grouped = false))
        assertEquals("45,99\u00A0грн", renderForm(NumberValue.Price(45, 99, Currency.Hryvnia), ',', grouped = false))
        assertEquals(
            "TSh\u00A012\u202F500",
            renderForm(NumberValue.Price(12_500, 0, Currency.Shilling), '.', grouped = true),
        )
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
        assertEquals(',', Numbers.pack("de").decimalMark)
        assertEquals('.', Numbers.pack("en").decimalMark)
        assertEquals(',', Numbers.pack("es").decimalMark)
        assertEquals('.', Numbers.pack("sw").decimalMark)
        assertEquals(',', Numbers.pack("uk").decimalMark)
        assertEquals(',', Numbers.pack("eo").decimalMark)
        assertEquals(',', Numbers.pack("fr").decimalMark)
        assertEquals(',', Numbers.pack("it").decimalMark)
    }

    // Sampling

    @Test
    fun everySampledFormTaskIsWellFormed() {
        val rng = Random(0xF04D)
        for (language in Numbers.languages) {
            for (sprosse in 1..Numbers.maxSprosse(NumbersReading.Form)) {
                repeat(50) {
                    val task = Numbers.sample(NumbersReading.Form, language, sprosse, rng)
                    val where = "$language Sprosse $sprosse ${task.prompt}"
                    assertEquals(NumbersReading.Form, task.kind, where)
                    assertEquals(language, task.language, where)
                    assertTrue(task.prompt.isNotEmpty(), where)
                    assertTrue(task.accepted.all { it.isNotEmpty() }, where)
                    assertTrue(task.display in task.accepted, "$where: ${task.display}")
                }
            }
        }
    }

    @Test
    fun sprossenClampInsteadOfThrowing() {
        for (language in authored) {
            assertEquals(
                Numbers.sample(NumbersReading.Form, language, 1, Random(3)),
                Numbers.sample(NumbersReading.Form, language, -3, Random(3)),
            )
            assertEquals(
                Numbers.sample(NumbersReading.Form, language, 10, Random(3)),
                Numbers.sample(NumbersReading.Form, language, 99, Random(3)),
            )
        }
    }

    @Test
    fun samplingIsDeterministicForSeededGenerator() {
        val a = Random(0xC0FFEE)
        val b = Random(0xC0FFEE)
        repeat(100) {
            assertEquals(
                Numbers.sample(NumbersReading.Form, "de", a),
                Numbers.sample(NumbersReading.Form, "de", b),
            )
        }
    }

    // Reverse

    private fun formTask(language: String, prompt: String, display: String = prompt): NumbersTask =
        NumbersTask(NumbersReading.Form, language, prompt, listOf("reading"), "reading", promptDisplay = display)

    @Test
    fun reversedFormsTakeTheNotationTheDrillDidNotAskAbout() {
        assertEquals(listOf("3,7", "3.7"), Numbers.reversed(formTask("de", "3,7")).accepted)
        assertEquals(listOf("3.7", "3,7"), Numbers.reversed(formTask("en", "3.7")).accepted)
        assertEquals(listOf("20.", "20"), Numbers.reversed(formTask("de", "20.")).accepted)
        assertEquals(
            listOf("45\u202F%", "45%", "45"),
            Numbers.reversed(formTask("de", "45\u202F%")).accepted,
        )
        assertEquals(
            listOf("3\u00D7", "3x", "3"),
            Numbers.reversed(formTask("de", "3\u00D7")).accepted,
        )
        assertEquals(listOf("-45"), Numbers.reversed(formTask("de", "-45")).accepted)
        // The tag is not the number: the bare amount grades, in either mark.
        assertEquals(
            listOf("3,50\u00A0€", "3,50 €", "3,50€", "3,50", "3.50"),
            Numbers.reversed(formTask("de", "3,50\u00A0€")).accepted,
        )
        assertEquals(listOf("$3.50", "3.50", "3,50"), Numbers.reversed(formTask("en", "$3.50")).accepted)
    }

    @Test
    fun aReversedFormRevealsTheGroupedWriting() {
        val back = Numbers.reversed(formTask("de", "-12345", "-12\u202F345"))
        assertEquals("-12\u202F345", back.display)
        assertEquals(listOf("-12\u202F345", "-12345"), back.accepted)
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
}
