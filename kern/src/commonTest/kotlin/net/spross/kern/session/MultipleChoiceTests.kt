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
    fun sentenceShapeIsReadOffTheClosingMark() {
        assertEquals(MultipleChoice.SentenceShape.Question, MultipleChoice.sentenceShape("Wo sind sie?"))
        assertEquals(MultipleChoice.SentenceShape.Question, MultipleChoice.sentenceShape("¿Cocinas arroz hoy?"))
        assertEquals(MultipleChoice.SentenceShape.Exclamation, MultipleChoice.sentenceShape("¡Buenos días!"))
        assertEquals(MultipleChoice.SentenceShape.Statement, MultipleChoice.sentenceShape("Ich koche Reis."))
        assertEquals(MultipleChoice.SentenceShape.Statement, MultipleChoice.sentenceShape("Einen Moment…"))
        assertEquals(MultipleChoice.SentenceShape.Bare, MultipleChoice.sentenceShape("Guten Tag"))
        assertEquals(MultipleChoice.SentenceShape.Bare, MultipleChoice.sentenceShape("Wasser"))
    }

    @Test
    fun trailingBlanksNeverChangeTheShape() {
        assertEquals(MultipleChoice.SentenceShape.Question, MultipleChoice.sentenceShape("Wer ist da? "))
        assertEquals(MultipleChoice.SentenceShape.Bare, MultipleChoice.sentenceShape(""))
    }

    // why: a lone question mark answers the question before it is read — the
    // far-off question outranks the statement of identical length.
    @Test
    fun aQuestionKeepsTheCompanyOfQuestions() {
        val picked = pick(
            answer = option("Wo sind sie?", CardKind.Phrase),
            candidates = listOf(
                option("Wer ist da?", CardKind.Phrase),
                option("Wo sind wir.", CardKind.Phrase),
            ),
            limit = 1,
        )
        assertEquals(listOf("Wer ist da?"), picked)
    }

    @Test
    fun anExclamationIsNotTheSameShapeAsAStatement() {
        val picked = pick(
            answer = option("Guten Morgen!", CardKind.Phrase),
            candidates = listOf(
                option("Ich bin müde.", CardKind.Phrase),
                option("Schlaf gut!", CardKind.Phrase),
            ),
            limit = 1,
        )
        assertEquals(listOf("Schlaf gut!"), picked)
    }

    @Test
    fun sentenceShapeOutranksTheArea() {
        val picked = pick(
            answer = option("Wo sind sie?", CardKind.Phrase, area = "questions"),
            candidates = listOf(
                option("Ich koche Reis.", CardKind.Phrase, area = "questions"),
                option("Wer kocht da?", CardKind.Phrase, area = "kitchen"),
            ),
            limit = 1,
        )
        assertEquals(listOf("Wer kocht da?"), picked)
    }

    @Test
    fun theWordClassStillOutranksTheSentenceShape() {
        val picked = pick(
            answer = option("Wo sind sie?", CardKind.Phrase),
            candidates = listOf(
                option("Wasser?", CardKind.Noun),
                option("Ich koche Reis.", CardKind.Phrase),
            ),
            limit = 1,
        )
        assertEquals(listOf("Ich koche Reis."), picked)
    }

    // The shape RANKS, it never filters: four tiles still fill from a box that
    // has nothing closing the same way left to offer.
    @Test
    fun aPoolThatSharesNoShapeStillFillsTheTiles() {
        val picked = pick(
            answer = option("Wo sind sie?", CardKind.Phrase),
            candidates = listOf(
                option("Ich koche Reis.", CardKind.Phrase),
                option("Schlaf gut!", CardKind.Phrase),
                option("Guten Tag", CardKind.Phrase),
            ),
        )
        assertEquals(3, picked.size)
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
