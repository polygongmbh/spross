package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The reveal lines both apps read: the plural, the family beyond it, and the line the card closes on. */
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
        val word = realization("die Verwaltung", teaches = listOf("das Amt", "die Behörde"))
        assertEquals(listOf("das Amt", "die Behörde"), alternates(word, listOf("die Verwaltung")))
    }

    /**
     * The regression: a rotated recognition prompt puts a SYNONYM on screen, and the line
     * used to offer it back as though it were another word — while dropping the citation
     * form the learner had not seen.
     */
    @Test
    fun theFormOnScreenNeverAppearsAmongItsOwnAlternatives() {
        val word = realization("die Verwaltung", teaches = listOf("das Amt", "die Behörde"))
        assertEquals(listOf("die Verwaltung", "die Behörde"), alternates(word, listOf("das Amt")))
    }

    @Test
    fun aWordWithNothingLeftToOfferHasNoAlternates() {
        val word = realization("das Amt", teaches = listOf("die Behörde"))
        assertEquals(emptyList<String>(), alternates(word, listOf("das Amt", "die Behörde")))
        assertEquals(emptyList<String>(), alternates(realization("nyumba"), listOf("nyumba")))
    }

    /** `accepts` grades an answer, it never teaches a form — the reveal must not list it. */
    @Test
    fun variantsStaySilent() {
        val word = Realization(
            lang = "de",
            text = "die Tür",
            teaches = listOf("die Türe"),
            accepts = listOf("die Tuer"),
        )
        assertEquals(listOf("die Türe"), alternates(word, listOf("die Tür")))
    }

    /** The card says its own thing where it has one; what the word also means takes the slot only where it has none. */
    @Test
    fun theClosingNoteIsOneLine() {
        val bank = realization("die Bank")
        assertEquals(ClosingNote.Own("Geldhaus"), closingNote(bank.copy(note = "Geldhaus"), listOf("Sitzbank")))
        assertEquals(ClosingNote.AlsoMeans(listOf("Sitzbank")), closingNote(bank, listOf("Sitzbank")))
        assertNull(closingNote(bank, emptyList()))
    }

    private fun realization(
        text: String,
        plural: String? = null,
        teaches: List<String> = emptyList(),
    ) = Realization(
        lang = "de",
        text = text,
        teaches = teaches,
        grammar = buildMap { plural?.let { put("plural", it) } },
    )
}
