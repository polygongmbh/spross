package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultipleChoiceTests {

    @Test
    fun distractorsExcludeTheAnswerCaseInsensitively() {
        val picked = MultipleChoice.distractors("Wasser", listOf("wasser", "Feuer", "WASSER"))
        assertEquals(listOf("Feuer"), picked)
    }

    @Test
    fun distractorsAreUniqueCaseInsensitively() {
        val picked = MultipleChoice.distractors("Hund", listOf("Katze", "katze", "Maus"))
        assertEquals(setOf("Katze", "Maus"), picked.toSet())
        assertEquals(2, picked.size)
    }

    @Test
    fun aPoolOfNothingButTheAnswerYieldsNoDistractor() {
        assertTrue(MultipleChoice.distractors("maji", listOf("maji", "MAJI")).isEmpty())
    }

    // why: a lone long option is a visual tell — the shortlist keeps the
    // closest shapes so length can't single the answer out.
    @Test
    fun closestShapesRankFirstAndTheOutliersFallOff() {
        val picked = MultipleChoice.distractors(
            answer = "Hut",
            candidates = listOf("Krankenhausverwaltung", "Uhr", "eine lange Wendung", "Bus", "Ast"),
            limit = 3,
        )
        assertEquals(listOf("Uhr", "Bus", "Ast"), picked)
    }

    @Test
    fun theShortlistIsCappedAtTheLimit() {
        val candidates = (1..20).map { "wort$it" }
        assertEquals(MultipleChoice.SHORTLIST, MultipleChoice.distractors("Wort", candidates).size)
        assertEquals(3, MultipleChoice.distractors("Wort", candidates, limit = 3).size)
    }

    @Test
    fun shapeDistanceGrowsWithLengthGap() {
        assertEquals(0, MultipleChoice.shapeDistance("Feuer", "Hunde"))
        assertTrue(
            MultipleChoice.shapeDistance("Feuer", "Wasser")
                < MultipleChoice.shapeDistance("Wasserhahn", "Wasser"),
        )
    }

    // A phrase among single words is as much a tell as a long one, so the part
    // count outweighs a closer character length.
    @Test
    fun differingPartCountOutweighsLength() {
        assertTrue(
            MultipleChoice.shapeDistance("Baumhaus", "Wasser")
                < MultipleChoice.shapeDistance("der Baum", "Wasser"),
        )
        assertTrue(
            MultipleChoice.shapeDistance("kwa heri", "kwa nini")
                < MultipleChoice.shapeDistance("habari", "kwa nini"),
        )
    }
}
