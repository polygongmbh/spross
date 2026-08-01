package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

/** The last rung: words out of the box, spoken once, typed back. */
class LetterDrillDictationTests {
    private val cards = LetterDrillFixture.dictationCards()

    private fun drawn(level: Int, from: List<Card> = cards, avoid: String? = null): List<String> =
        (1..200).map { LetterDrill.sampleDictation(from, level, avoid, Random(it)).display }

    @Test
    fun onlySingleWordsAreEverDictated() {
        val phrase = LetterDrillFixture.card("greeting", "Guten Tag")
        val texts = drawn(9, from = cards + phrase).toSet()
        assertTrue(texts.none { ' ' in it }, "a phrase reached a transcription task: $texts")
        assertTrue(texts.size > 1)
    }

    @Test
    fun theShortRungAsksForShortWords() {
        val texts = drawn(8).toSet()
        assertTrue(
            texts.all { it.length <= 6 },
            "level 8 dictated something long: $texts",
        )
        assertTrue("Sonne" in texts && "Regenbogen" !in texts)
    }

    @Test
    fun theShortFilterWidensRatherThanDrawFromOneWord() {
        // Only two short words in the whole box: filtering would dictate the same pair
        // forever, so the rung takes the long ones instead.
        val narrow = listOf(
            LetterDrillFixture.card("ice", "Eis"),
            LetterDrillFixture.card("house", "Haus"),
            LetterDrillFixture.card("window", "Fenster"),
            LetterDrillFixture.card("rainbow", "Regenbogen"),
        )
        val texts = drawn(8, from = narrow).toSet()
        assertTrue("Regenbogen" in texts, "the filter must widen below three candidates: $texts")
    }

    @Test
    fun theLastRungAsksAnyConsolidatedWord() {
        val texts = drawn(9).toSet()
        assertTrue("Regenbogen" in texts, "level 9 takes the long ones too: $texts")
    }

    @Test
    fun theTaskSpeaksTheCardAndKeepsItsMeaningBack() {
        val task = LetterDrill.sampleDictation(cards, 9, null, Random(3))
        val card = cards.first { it.id == task.answerRef }
        assertEquals(LetterStage.Dictation, task.stage)
        assertEquals(card.target.lang, task.language)
        assertEquals(card.target.text, task.promptText)
        assertEquals(LetterPromptKind.Word, task.promptKind)
        assertEquals(card.id, task.promptSlug)
        assertNull(task.promptGlyph)
        // No tiles, no gap, no meaning on screen: the audio is the whole question.
        assertNull(task.choices)
        assertNull(task.gapText)
        // Transcription accepts what was spoken and nothing else.
        assertEquals(listOf(card.target.text), task.accepted)
        assertEquals(card.target.text, task.display)
        assertEquals(card.source.text, task.gloss)
    }

    @Test
    fun theWordJustDictatedIsResampledOnce() {
        val repeats = drawn(9, avoid = "ice").count { it == "Eis" }
        assertTrue(repeats < 200 / cards.size, "avoiding the last word left $repeats repeats of 200")
    }

    @Test
    fun theGradingCardKeepsTheRealIdentityAndNarrowsOnlyTheAnswer() {
        val real = Card(
            id = "people/teacher-f",
            kind = CardKind.Verb,
            area = "people",
            emoji = null,
            seedIndex = 4,
            components = emptyList(),
            feminineOf = "people/teacher",
            baseAccepted = listOf("Lehrer"),
            source = Realization(lang = "en", text = "teacher"),
            target = Realization(
                lang = "xx",
                text = "Lehrerin",
                synonyms = listOf("Dozentin"),
                variants = listOf("Lehrerinnen"),
            ),
            promptFeminineMarker = true,
        )
        val task = LetterDrill.sampleDictation(listOf(real), 9, null, Random(1))
        val grading = LetterDrill.dictationGradingCard(real, task)

        // The identity survives — the grader skips the prompted concept by id, and the
        // learner's OWN word must never come back named as somebody else's.
        assertEquals(real.id, grading.id)
        assertEquals(real.feminineOf, grading.feminineOf)
        assertEquals(real.kind, grading.kind)
        // Only the answer set narrows: a synonym is a different word than the one played.
        assertEquals("Lehrerin", grading.target.text)
        assertTrue(grading.target.synonyms.isEmpty() && grading.target.variants.isEmpty())
        assertTrue(grading.baseAccepted.isEmpty(), "the base word is not what was spoken either")
    }
}
