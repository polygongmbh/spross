package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The vocabulary spec for Esperanto's number forms, authored from the language research.
 * It is never read back off the generator: a reading that changes here is a claim about the
 * language, not about the code.
 * The helpers it asserts through are `NumberFormsFixture`'s.
 */
class TrainerEsperantoFormsTests {

    @Test
    fun esperantoNegativesAreMinusPlusTheCardinal() {
        assertCanonical("eo", NumberValue.Negative(7), "minus sep")
        assertCanonical("eo", NumberValue.Negative(45), "minus kvardek kvin")
        assertCanonical("eo", NumberValue.Negative(1234), "minus mil ducent tridek kvar")
        // The x-system spelling grades behind the form word exactly as it does bare.
        assertAccepts("eo", NumberValue.Negative(9), "minus naux")
        assertRejects("eo", NumberValue.Negative(7), "malplus sep", "negativa sep")
    }

    @Test
    fun esperantoDecimalsReadEachDigitAfterKomo() {
        assertCanonical("eo", NumberValue.Decimal(3, "7"), "tri komo sep")
        assertCanonical("eo", NumberValue.Decimal(3, "45"), "tri komo kvar kvin")
        assertAccepts("eo", NumberValue.Decimal(3, "45"), "tri komo kvardek kvin")
        assertCanonical("eo", NumberValue.Decimal(0, "05"), "nulo komo nulo kvin")
        assertCanonical("eo", NumberValue.Decimal(12, "30"), "dek du komo tri nulo")
        // A leading zero names a different number, so the run-together reading stops.
        assertRejects("eo", NumberValue.Decimal(0, "05"), "nulo komo kvin")
    }

    /** procento is a counted noun, so it pluralizes; elcento is the same word, built at home. */
    @Test
    fun esperantoPercentCountsTheNoun() {
        assertCanonical("eo", NumberValue.Percent(1), "unu procento")
        assertCanonical("eo", NumberValue.Percent(25), "dudek kvin procentoj")
        assertCanonical("eo", NumberValue.Percent(100), "cent procentoj")
        assertAccepts("eo", NumberValue.Percent(1), "unu elcento")
        assertAccepts("eo", NumberValue.Percent(25), "dudek kvin elcentoj")
        assertRejects("eo", NumberValue.Percent(25), "dudek kvin procento")
    }

    /**
     * Frequency is the adverbial accusative, which every numeral takes; the -foje adverb
     * rides along only where the numeral is one word, since that is as far as it welds.
     */
    @Test
    fun esperantoMultiplicativesCountFojoInTheAccusative() {
        assertCanonical("eo", NumberValue.Multiplicative(1), "unu fojon")
        assertCanonical("eo", NumberValue.Multiplicative(3), "tri fojojn")
        assertCanonical("eo", NumberValue.Multiplicative(20), "dudek fojojn")
        assertCanonical("eo", NumberValue.Multiplicative(21), "dudek unu fojojn")
        assertAccepts("eo", NumberValue.Multiplicative(1), "unufoje")
        assertAccepts("eo", NumberValue.Multiplicative(3), "trifoje")
        assertRejects("eo", NumberValue.Multiplicative(21), "dudek unufoje")
        assertRejects("eo", NumberValue.Multiplicative(3), "tri fojoj")
        // -obl- is a factor, not a count of occasions — the Ukrainian удвічі refusal again.
        assertRejects("eo", NumberValue.Multiplicative(3), "trioble")
    }

    @Test
    fun esperantoFractionsAreOnNouns() {
        assertCanonical("eo", NumberValue.Fraction(1, 2), "duono")
        assertCanonical("eo", NumberValue.Fraction(1, 4), "kvarono")
        assertCanonical("eo", NumberValue.Fraction(2, 3), "du trionoj")
        assertCanonical("eo", NumberValue.Fraction(3, 4), "tri kvaronoj")
        assertCanonical("eo", NumberValue.Fraction(1, 9), "naŭono")
        assertAccepts("eo", NumberValue.Fraction(1, 4), "unu kvarono")
        assertAccepts("eo", NumberValue.Fraction(1, 9), "nauxono")
        // A compound denominator closes up: spaced, "dek duono" would name ten halves.
        assertCanonical("eo", NumberValue.Fraction(1, 11), "dek-unuono")
        assertCanonical("eo", NumberValue.Fraction(5, 12), "kvin dek-duonoj")
        assertRejects("eo", NumberValue.Fraction(5, 12), "kvin dek duonoj")
        assertRejects("eo", NumberValue.Fraction(2, 3), "du triono")
    }

    /** The ordinal is the whole numeral hyphenated and given -a, with no ceiling to author. */
    @Test
    fun esperantoOrdinalsSuffixTheWholeNumeral() {
        assertCanonical("eo", NumberValue.Ordinal(1), "unua")
        assertCanonical("eo", NumberValue.Ordinal(3), "tria")
        assertCanonical("eo", NumberValue.Ordinal(9), "naŭa")
        assertCanonical("eo", NumberValue.Ordinal(10), "deka")
        assertCanonical("eo", NumberValue.Ordinal(11), "dek-unua")
        assertCanonical("eo", NumberValue.Ordinal(20), "dudeka")
        assertCanonical("eo", NumberValue.Ordinal(21), "dudek-unua")
        assertCanonical("eo", NumberValue.Ordinal(99), "naŭdek-naŭa")
        assertCanonical("eo", NumberValue.Ordinal(100), "centa")
        assertAccepts("eo", NumberValue.Ordinal(9), "nauxa")
        // A numeral taking an ending is written closed up, so the spaced form is no reading.
        assertRejects("eo", NumberValue.Ordinal(21), "dudek unua")
        assertRejects("eo", NumberValue.Ordinal(1), "unu")
    }

    /** Nothing about the affixes runs out, so the pack authors no reach of its own. */
    @Test
    fun esperantoDrawsEveryFormOverTheDrillsOwnReach() {
        val limits = Trainer.pack("eo").formLimits
        assertEquals(NumberForm.entries.toSet(), limits.forms)
        assertEquals((2..12).toSet(), limits.fractionDenominators)
        assertEquals(1L..100L, limits.ordinalRange)
    }

    /**
     * A forms task names the form it asks, so the app can introduce a mark the first time
     * it appears instead of leaving it to be discovered in a failure. Every other kind
     * leaves the key null — nothing else has a notation to introduce.
     */
    @Test
    fun everyFormsTaskNamesItsForm() {
        val rng = Random(41)
        val keys = mutableSetOf<String>()
        repeat(400) {
            val task = Trainer.sample(TrainerKind.Forms, "de", FORMS_MAX_LEVEL, rng)
            keys += assertNotNull(task.formKey, task.prompt)
        }
        assertEquals(NumberForm.entries.map { it.key }.toSet(), keys)
        for (kind in listOf(TrainerKind.Numbers, TrainerKind.Years, TrainerKind.Clock)) {
            assertNull(Trainer.sample(kind, "de", 1, Random(7)).formKey, "$kind")
        }
    }
}
