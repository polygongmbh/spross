package net.spross.kern.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.CardKind

class MultipleChoiceTests {

    private fun option(
        text: String,
        kind: CardKind = CardKind.Noun,
        area: String = "kitchen",
    ) = MultipleChoice.Option(text, kind, area)

    private fun pick(
        answer: MultipleChoice.Option,
        candidates: List<MultipleChoice.Option>,
        limit: Int = MultipleChoice.SHORTLIST,
    ) = MultipleChoice.distractors(answer, candidates, limit)

    private fun pick(answer: String, candidates: List<String>, limit: Int = MultipleChoice.SHORTLIST) =
        pick(option(answer), candidates.map { option(it) }, limit)

    @Test
    fun distractorsExcludeTheAnswerCaseInsensitively() {
        assertEquals(listOf("Feuer"), pick("Wasser", listOf("wasser", "Feuer", "WASSER")))
    }

    @Test
    fun distractorsAreUniqueCaseInsensitively() {
        val picked = pick("Hund", listOf("Katze", "katze", "Maus"))
        assertEquals(setOf("Katze", "Maus"), picked.toSet())
        assertEquals(2, picked.size)
    }

    @Test
    fun aPoolOfNothingButTheAnswerYieldsNoDistractor() {
        assertTrue(pick("maji", listOf("maji", "MAJI")).isEmpty())
    }

    // why: a lone long option is a visual tell — the shortlist keeps the
    // closest shapes so length can't single the answer out.
    @Test
    fun closestShapesRankFirstAndTheOutliersFallOff() {
        val picked = pick(
            answer = "Hut",
            candidates = listOf("Krankenhausverwaltung", "Uhr", "eine lange Wendung", "Bus", "Ast"),
            limit = 3,
        )
        assertEquals(listOf("Uhr", "Bus", "Ast"), picked)
    }

    @Test
    fun theShortlistIsCappedAtTheLimit() {
        val candidates = (1..20).map { "wort$it" }
        assertEquals(MultipleChoice.SHORTLIST, pick("Wort", candidates).size)
        assertEquals(3, pick("Wort", candidates, limit = 3).size)
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

    // A verb among nouns is answerable off the ku- alone, so the class outranks
    // every shape: the far-off verb beats the noun of identical length.
    @Test
    fun sameWordClassOutranksShape() {
        val picked = pick(
            answer = option("kupika", CardKind.Verb),
            candidates = listOf(
                option("kisu", CardKind.Noun),
                option("kunywa", CardKind.Verb),
                option("kula", CardKind.Verb),
            ),
            limit = 2,
        )
        assertEquals(listOf("kunywa", "kula"), picked)
    }

    @Test
    fun sameAreaBreaksTheTieWithinAClass() {
        val picked = pick(
            answer = option("kisu", area = "kitchen"),
            candidates = listOf(
                option("mlango", area = "hall"),
                option("sufuria", area = "kitchen"),
            ),
            limit = 1,
        )
        assertEquals(listOf("sufuria"), picked)
    }

    @Test
    fun areaNeverOutranksTheWordClass() {
        val picked = pick(
            answer = option("kupika", CardKind.Verb, area = "kitchen"),
            candidates = listOf(
                option("kisu", CardKind.Noun, area = "kitchen"),
                option("kusoma", CardKind.Verb, area = "school"),
            ),
            limit = 1,
        )
        assertEquals(listOf("kusoma"), picked)
    }

    @Test
    fun aBoundStemIsOfferedWithoutItsDash() {
        assertEquals("zuri", MultipleChoice.optionForm("-zuri", CardKind.Adjective, emptyList()))
    }

    @Test
    fun aVerbIsOfferedWithoutItsCitationPrefix() {
        assertEquals("pika", MultipleChoice.optionForm("kupika", CardKind.Verb, listOf("ku", "kw")))
        assertEquals("enda", MultipleChoice.optionForm("kwenda", CardKind.Verb, listOf("ku", "kw")))
        assertEquals("cook", MultipleChoice.optionForm("to cook", CardKind.Verb, listOf("to ")))
    }

    // why: prefix stripping is the VERB rule — a noun that merely starts like a
    // citation form keeps its first syllable, exactly as grading leniency does.
    @Test
    fun aNounThatStartsLikeAStemKeepsIt() {
        assertEquals("kuku", MultipleChoice.optionForm("kuku", CardKind.Noun, listOf("ku", "kw")))
    }

    @Test
    fun aWordThatIsNothingButItsPrefixSurvivesWhole() {
        assertEquals("ku", MultipleChoice.optionForm("ku", CardKind.Verb, listOf("ku")))
    }
}
