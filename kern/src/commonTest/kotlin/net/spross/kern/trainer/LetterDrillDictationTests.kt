package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

/** The last Sprosse: words out of the box, spoken once, typed back. */
class LetterDrillDictationTests {
    private val cards = LetterDrillFixture.dictationCards()

    private fun drawn(sprosse: Int, from: List<Card> = cards, avoid: String? = null): List<String> =
        (1..200).map {
            assertNotNull(
                LetterDrill.sampleDictation(
                    LetterDrillFixture.dictationCandidates(from), null, sprosse, avoid, emptySet(), Random(it),
                ),
            ).display
        }

    @Test
    fun onlySingleWordsAreEverDictated() {
        val phrase = LetterDrillFixture.card("greeting", "Guten Tag")
        val texts = drawn(9, from = cards + phrase).toSet()
        assertTrue(texts.none { ' ' in it }, "a phrase reached a transcription task: $texts")
        assertTrue(texts.size > 1)
    }

    @Test
    fun theShortSprosseAsksForShortWords() {
        val texts = drawn(8).toSet()
        assertTrue("Sonne" in texts && "Regenbogen" !in texts, "$texts")
    }

    @Test
    fun theShortFilterWidensRatherThanDrawFromOneWord() {
        // Only two short words in the whole box: filtering would dictate the same pair
        // forever, so the Sprosse takes the long ones instead.
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
    fun theLastSprosseAsksAnyGrowingWord() {
        val texts = drawn(9).toSet()
        assertTrue("Regenbogen" in texts, "Sprosse 9 takes the long ones too: $texts")
    }

    @Test
    fun theTaskSpeaksTheCardAndKeepsItsMeaningBack() {
        val task = assertNotNull(
            LetterDrill.sampleDictation(
                LetterDrillFixture.dictationCandidates(cards), null, 9, null, emptySet(), Random(3),
            ),
        )
        val card = cards.first { it.id == task.answerRef }
        assertEquals(LetterFormat.Dictation, task.format)
        assertEquals(card.target.lang, task.language)
        assertEquals(card.target.text, task.promptText)
        assertEquals(card.id, task.promptSlug)
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

    /** The two things that add to a word's weight, and the floor nothing falls below. */
    @Test
    fun theWeightNamesSpellingAndDifficulty() {
        fun weight(
            text: String,
            tricky: List<String> = listOf("ch", "ß"),
            difficulty: Double = 0.0,
        ) = LetterDictation.weight(
            LetterDrill.DictationCandidate(LetterDrillFixture.card("x", text), difficulty),
            tricky,
        )
        val plain = weight("Haus")
        assertEquals(1, plain, "a clean plain word is the floor, never excluded")
        assertTrue(weight("Buch") > plain, "a hard grapheme")
        // The count is of GRAPHEMES carried, not of their occurrences.
        assertEquals(weight("Buch"), weight("Buchbuch"))
        assertTrue(weight("Buchstraße") > weight("Buch"), "two of them")
        assertTrue(weight("Haus", difficulty = 7.0) > plain)
        // Each term is capped, so no single one can take the Sprosse over on its own.
        assertEquals(
            weight("abcd", tricky = listOf("a", "b", "c", "d")),
            weight("abcdefgh", tricky = listOf("a", "b", "c", "d", "e", "f", "g", "h")),
        )
    }

    /**
     * The point of all of it: over a run, the words that are hard to spell and the words
     * this learner keeps forgetting come up more than the easy clean one.
     */
    @Test
    fun aHardWordIsDictatedMoreOftenThanAnEasyOne() {
        val alphabet = LetterDrillFixture.alphabet
        val pool = listOf(
            LetterDrill.DictationCandidate(LetterDrillFixture.card("easy", "Haus")),
            LetterDrill.DictationCandidate(LetterDrillFixture.card("spelt", "Buch")),
            LetterDrill.DictationCandidate(LetterDrillFixture.card("lost", "Sonne"), difficulty = 8.0),
        )
        val rng = Random(11)
        val drawn = (1..600).map {
            assertNotNull(LetterDrill.sampleDictation(pool, alphabet, 9, null, emptySet(), rng)).answerRef
        }
        val easy = drawn.count { it == "easy" }
        assertTrue(drawn.count { it == "spelt" } > easy, "a tricky spelling must out-draw a plain one")
        assertTrue(drawn.count { it == "lost" } > easy, "a forgotten word must out-draw a kept one")
        assertTrue(easy > 0, "and nothing is ever shut out")
    }

    /** No alphabet, clean schedules: the draw is exactly the uniform one it always was. */
    @Test
    fun anUnweightedPoolDrawsUniformly() {
        val counts = drawn(9).groupingBy { it }.eachCount()
        assertEquals(cards.size, counts.size, "every word must come up: $counts")
        assertTrue(counts.values.all { it > 200 / cards.size / 3 }, "lopsided without a weight: $counts")
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
                teaches = listOf("Dozentin"),
                accepts = listOf("Lehrerinnen"),
            ),
            promptFeminineMarker = true,
        )
        val task = assertNotNull(
            LetterDrill.sampleDictation(
                LetterDrillFixture.dictationCandidates(listOf(real)), null, 9, null, emptySet(), Random(1),
            ),
        )
        val grading = LetterDrill.dictationGradingCard(real, task)

        // The identity survives — the grader skips the prompted concept by id, and the
        // learner's OWN word must never come back named as somebody else's.
        assertEquals(real.id, grading.id)
        assertEquals(real.feminineOf, grading.feminineOf)
        assertEquals(real.kind, grading.kind)
        // Only the answer set narrows: a synonym is a different word than the one played.
        assertEquals("Lehrerin", grading.target.text)
        assertTrue(grading.target.teaches.isEmpty() && grading.target.accepts.isEmpty())
        assertTrue(grading.baseAccepted.isEmpty(), "the base word is not what was spoken either")
    }
}
