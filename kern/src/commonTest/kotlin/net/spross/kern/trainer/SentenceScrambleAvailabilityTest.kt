package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Which phrases the sentence scramble may ask. Every row here is a shape the gate has an
 * opinion about: a locked phrase, a component-free one, one too short to have an order, an
 * authored fill-in-blank pattern, and a suspended one.
 */
class SentenceScrambleAvailabilityTest {

    private val words = listOf(
        ScrambleFixture.word("mouse", "Maus", seed = 1),
        ScrambleFixture.word("run", "laufen", seed = 2),
        ScrambleFixture.word("slow", "langsam", seed = 3),
    )

    private val phrases = listOf(
        ScrambleFixture.phrase("runs", "die Maus läuft", listOf("mouse", "run"), seed = 10),
        ScrambleFixture.phrase("runs-slow", "die Maus läuft sehr langsam", listOf("mouse", "run"), seed = 11),
        ScrambleFixture.phrase("locked", "der Hund schläft hier", listOf("nothing-held"), seed = 12),
        ScrambleFixture.phrase("greeting", "guten Morgen alle", emptyList(), seed = 13),
        ScrambleFixture.phrase("short", "na und", listOf("mouse", "run"), seed = 14),
        ScrambleFixture.phrase("blank", "die … läuft schnell", listOf("mouse", "run"), seed = 15),
        ScrambleFixture.phrase("sleeps", "die Maus schläft dort", listOf("mouse", "run"), seed = 16),
        ScrambleFixture.phrase("careful", "Vorsicht, heiß!", listOf("mouse", "run"), seed = 17),
        ScrambleFixture.phrase("mouse-sleeps", "Die Maus schläft gern.", listOf("mouse", "run"), seed = 18),
        ScrambleFixture.phrase("mouse-eats", "Maus und Hund fressen.", listOf("mouse", "run"), seed = 19),
    )

    private fun report(
        standing: Map<String, Double> = emptyMap(),
        suspended: Set<String> = emptySet(),
    ) = SentenceScrambleAvailability.report(
        ScrambleFixture.box(words + phrases, standing = standing, suspended = suspended),
    )

    /** An unlocked phrase of at least three atoms is asked, in seed order, already cut up. */
    @Test
    fun everyUnlockedPhraseWithAWordOrderIsAsked() {
        val report = report()
        assertEquals(
            listOf("runs", "runs-slow", "sleeps", "mouse-sleeps", "mouse-eats"),
            report.phrases.map { it.card.id },
        )
        assertEquals(listOf("die", "Maus", "läuft"), report.phrases.first().atoms.map { it.text })
    }

    /** A phrase whose components the learner does not hold is words they cannot arrange. */
    @Test
    fun aLockedPhraseIsNotAsked() {
        assertFalse("locked" in report().phrases.map { it.card.id })
        // The gate is the components, so letting one fall back below the growing bar re-locks it.
        val shaky = report(standing = mapOf("run" to 1.0))
        assertEquals(emptyList(), shaky.phrases.map { it.card.id })
    }

    /** A component-free phrase never takes the unlock path, so it is never arranged either. */
    @Test
    fun aComponentFreePhraseIsNotAsked() {
        assertFalse("greeting" in report().phrases.map { it.card.id })
    }

    /** Below three WORDS there is no order to put back — the marks placed alongside are not one. */
    @Test
    fun aPhraseTooShortToHaveAnOrderIsNotAsked() {
        assertFalse("short" in report().phrases.map { it.card.id })
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS - 1, ScrambleTokenizer.tokens("na und").size)
        assertFalse("careful" in report().phrases.map { it.card.id })
        assertEquals(4, ScrambleTokenizer.atoms("Vorsicht, heiß!").size, "two words and two marks")
    }

    /** The ellipsis is an authored blank: the words around it are a frame, not a sentence. */
    @Test
    fun aFillInBlankPatternIsNotAsked() {
        assertFalse("blank" in report().phrases.map { it.card.id })
    }

    /** Suspending a phrase says stop asking it. */
    @Test
    fun aSuspendedPhraseIsNotAsked() {
        assertFalse("runs" in report(suspended = setOf("runs")).phrases.map { it.card.id })
    }

    /**
     * The leading chip loses a capital that is only its POSITION — but a German noun keeps the
     * one German spells it with wherever it stands, or the drill would teach the wrong spelling.
     */
    @Test
    fun theLeadingChipLosesAPositionalCapitalAndKeepsAnOwnOne() {
        val phrases = report().phrases.associateBy { it.card.id }
        val article = assertNotNull(phrases["mouse-sleeps"])
        assertEquals(listOf("die", "Maus", "schläft", "gern"), article.atoms.map { it.text })
        val noun = assertNotNull(phrases["mouse-eats"])
        assertEquals(listOf("Maus", "und", "Hund", "fressen"), noun.atoms.map { it.text })
    }

    /** The ceiling is the longest phrase the box actually holds, not a number. */
    @Test
    fun theLadderTopsOutOnTheLongestPhraseHeld() {
        val report = report()
        assertEquals(5, report.phrases.maxOf { it.words })
        assertEquals(3, report.maxLevel)
        assertEquals(SentenceScrambleAvailability.MIN_ATOMS, report.atomsAt(1))
        assertEquals(5, report.atomsAt(3))
    }

    /** The chip predicate is the pool floor, and an empty box offers nothing. */
    @Test
    fun theChipWaitsForAPoolWorthARun() {
        assertTrue(SentenceScrambleAvailability.drillExists(ScrambleFixture.box(words + phrases)))
        val thin = words + phrases.filter { it.id == "runs" }
        assertFalse(SentenceScrambleAvailability.drillExists(ScrambleFixture.box(thin)))
        assertFalse(SentenceScrambleAvailability.drillExists(ScrambleFixture.box(emptyList())))
    }
}
