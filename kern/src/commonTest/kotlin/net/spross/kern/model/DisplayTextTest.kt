package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The two reveal lines both apps used to carry their own copy of: the plural, and the family beyond it. */
class DisplayTextTest {

    @Test
    fun theSentinelsStaySentinels() {
        assertEquals(PluralForm.SameAsSingular, pluralForm(realization("der Lehrer", plural = "=")))
        assertEquals(PluralForm.PluralOnly, pluralForm(realization("die Eltern", plural = "only")))
    }

    @Test
    fun aSuffixPluralResolvesAgainstTheWord() {
        assertEquals(
            PluralForm.Form("die Lehrerinnen"),
            pluralForm(realization("die Lehrerin", plural = "-nen")),
        )
    }

    @Test
    fun aFullFormIsTakenAsAuthored() {
        assertEquals(PluralForm.Form("die Häuser"), pluralForm(realization("das Haus", plural = "die Häuser")))
    }

    /** An authored-but-empty plural is not a form — it used to render a bare label. */
    @Test
    fun anEmptyPluralIsNoPluralAtAll() {
        assertNull(pluralForm(realization("nyumba", plural = "")))
        assertNull(pluralForm(realization("nyumba")))
    }

    @Test
    fun theFamilyIsWhateverTheLearnerIsNotLookingAt() {
        val word = realization("die Verwaltung", synonyms = listOf("das Amt", "die Behörde"))
        assertEquals(listOf("das Amt", "die Behörde"), alternates(word, listOf("die Verwaltung")))
    }

    /**
     * The regression: a rotated recognition prompt puts a SYNONYM on screen, and the line
     * used to offer it back as though it were another word — while dropping the citation
     * form the learner had not seen.
     */
    @Test
    fun theFormOnScreenNeverAppearsAmongItsOwnAlternatives() {
        val word = realization("die Verwaltung", synonyms = listOf("das Amt", "die Behörde"))
        assertEquals(listOf("die Verwaltung", "die Behörde"), alternates(word, listOf("das Amt")))
    }

    @Test
    fun aWordWithNothingLeftToOfferHasNoAlternates() {
        val word = realization("das Amt", synonyms = listOf("die Behörde"))
        assertEquals(emptyList<String>(), alternates(word, listOf("das Amt", "die Behörde")))
        assertEquals(emptyList<String>(), alternates(realization("nyumba"), listOf("nyumba")))
    }

    /** Variants grade an answer, they never teach a form — the reveal must not list them. */
    @Test
    fun variantsStaySilent() {
        val word = Realization(
            lang = "de",
            text = "die Tür",
            synonyms = listOf("die Türe"),
            variants = listOf("die Tuer"),
        )
        assertEquals(listOf("die Türe"), alternates(word, listOf("die Tür")))
    }

    private fun realization(
        text: String,
        plural: String? = null,
        synonyms: List<String> = emptyList(),
    ) = Realization(
        lang = "de",
        text = text,
        synonyms = synonyms,
        grammar = buildMap { plural?.let { put("plural", it) } },
    )
}
