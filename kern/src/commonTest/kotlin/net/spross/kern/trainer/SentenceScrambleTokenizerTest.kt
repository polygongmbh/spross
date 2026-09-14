package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a phrase becomes atoms, and what an arrangement of them is worth. The two rules that
 * matter: rejoining gives the phrase back, and a repeated word is still two different atoms.
 */
class SentenceScrambleTokenizerTest {

    /** A mark stands as a chip of its own, and the join puts it back where it was cut from. */
    @Test
    fun aMarkStandsAsItsOwnChipAndTheJoinGivesThePhraseBack() {
        val text = "Wie geht es dir, mein Freund?"
        val atoms = ScrambleTokenizer.atoms(text)
        assertEquals(listOf("Wie", "geht", "es", "dir", ",", "mein", "Freund", "?"), atoms.map { it.text })
        assertEquals(text, ScrambleTokenizer.joined(atoms))
    }

    /** Spanish leads its question, so the mark that opens one rejoins ahead of the word. */
    @Test
    fun anOpeningMarkRejoinsAheadOfItsWord() {
        val text = "¿Cómo estás hoy?"
        val atoms = ScrambleTokenizer.atoms(text)
        assertEquals(listOf("¿", "Cómo", "estás", "hoy", "?"), atoms.map { it.text })
        assertEquals(text, ScrambleTokenizer.joined(atoms))
    }

    /**
     * The full stop is the one mark dropped outright: it marks where the sentence ends, which
     * is the question. A period spelling a word keeps its place.
     */
    @Test
    fun theSentencesOwnFullStopIsDropped() {
        assertEquals(
            listOf("die", "Maus", "läuft"),
            ScrambleTokenizer.atoms("die Maus läuft.").map { it.text },
        )
        assertEquals(
            listOf("nine", "a.m.", "sharp"),
            ScrambleTokenizer.atoms("nine a.m. sharp").map { it.text },
        )
    }

    /** Runs of whitespace collapse — the one way the round trip may differ from what was authored. */
    @Test
    fun whitespaceRunsCollapse() {
        assertEquals("ich bin da", ScrambleTokenizer.joined(ScrambleTokenizer.atoms("  ich   bin\tda ")))
    }

    /** A word the phrase prints twice is two atoms, and they are told apart by position. */
    @Test
    fun aRepeatedWordIsTwoAtoms() {
        val atoms = ScrambleTokenizer.atoms("der Mann und der Hund")
        assertEquals(listOf(0, 1, 2, 3, 4), atoms.map { it.id })
        assertEquals(atoms[0].text, atoms[3].text)
    }

    /** The authored order is the answer, and only in that order. */
    @Test
    fun onlyTheAuthoredOrderIsSolved() {
        val atoms = ScrambleTokenizer.atoms("der Mann und der Hund")
        assertTrue(ScrambleGrading.isSolved(atoms, atoms))
        assertFalse(ScrambleGrading.isSolved(atoms.reversed(), atoms))
        assertFalse(ScrambleGrading.isSolved(atoms.dropLast(1), atoms))
    }

    /**
     * The two identical words swapped read exactly right, so they ARE right: the ids are how the
     * run tells the atoms apart, never a second thing the learner has to get correct.
     */
    @Test
    fun swappingTwoIdenticalWordsStillReadsAsThePhrase() {
        val atoms = ScrambleTokenizer.atoms("der Mann und der Hund")
        val swapped = listOf(atoms[3], atoms[1], atoms[2], atoms[0], atoms[4])
        assertEquals(ScrambleTokenizer.joined(atoms), ScrambleTokenizer.joined(swapped))
        assertTrue(ScrambleGrading.isSolved(swapped, atoms))
    }

    /** Two DIFFERENT words swapped is the mistake the drill is there to catch. */
    @Test
    fun swappingTwoDifferentWordsIsNotSolved() {
        val atoms = ScrambleTokenizer.atoms("der Mann und der Hund")
        val swapped = listOf(atoms[0], atoms[4], atoms[2], atoms[3], atoms[1])
        assertFalse(ScrambleGrading.isSolved(swapped, atoms))
    }
}
