package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.CardKind

/**
 * Which of the learner's own words the scramble may ask. Every row here is a shape the gate has
 * an opinion about: the three word kinds, a phrase, a word short of the display bar, a word of
 * two tokens, and a word too short to anchor.
 */
class WordScrambleAvailabilityTest {

    private val shapes = listOf(
        ScrambleFixture.word("window", "Fenster", seed = 1),
        ScrambleFixture.word("cook", "kochen", CardKind.Verb, seed = 2),
        ScrambleFixture.word("fast", "schnell", CardKind.Adjective, seed = 3),
        ScrambleFixture.word("rainbow", "Regenbogen", seed = 4),
        ScrambleFixture.word("outside", "draußen", CardKind.Adjective, seed = 5),
        ScrambleFixture.word("wash", "sich waschen", CardKind.Verb, seed = 6),
        ScrambleFixture.word("clock", "Uhr", seed = 7),
        ScrambleFixture.word("sun", "Sonne", seed = 8),
        ScrambleFixture.phrase("greeting", "guten Morgen hier", listOf("window"), seed = 9),
        ScrambleFixture.word("shirt", "T-Shirt", seed = 10),
        ScrambleFixture.word(
            "bad",
            "-baya",
            CardKind.Adjective,
            seed = 11,
            accepts = listOf("mbi", "mbaya", "wabaya"),
        ),
    )

    /** The shapes plus depth enough to open on: the pool floor is a count, and counts need one. */
    private val cards = shapes + ScrambleFixture.filler(count = 11, letters = 6, fromSeed = 100)

    private fun report(
        standing: Map<String, Double> = emptyMap(),
        suspended: Set<String> = emptySet(),
    ) = WordScrambleAvailability.report(
        ScrambleFixture.box(cards, standing = standing, suspended = suspended),
    )

    private fun ids(
        standing: Map<String, Double> = emptyMap(),
        suspended: Set<String> = emptySet(),
    ): List<String> = report(standing, suspended).words.map { it.card.id }

    /** All three word kinds qualify, and the pool comes back in seed order — the padding sorts behind. */
    @Test
    fun everyConsolidatedSingleWordOfEnoughLettersIsAsked() {
        val shaped = listOf("window", "cook", "fast", "rainbow", "outside", "sun", "bad")
        assertEquals(shaped, ids().take(shaped.size))
    }

    /**
     * A bound stem is no citation form to write down, so the drill asks it through the concrete
     * forms it agrees into — and only through the ones that are themselves long enough to mix.
     */
    @Test
    fun aBoundStemIsAskedThroughItsAgreeingForms() {
        val stem = report().words.single { it.card.id == "bad" }
        assertEquals(listOf("mbaya", "wabaya"), stem.forms)
    }

    /** A spelling carrying anything but letters is nothing loose letters can be handed over as. */
    @Test
    fun aWordThatIsNotSpelledInLettersAloneIsNotAsked() {
        assertFalse("shirt" in ids())
        assertEquals(emptyList(), WordScrambleAvailability.spellings(cards.single { it.id == "shirt" }))
    }

    /** The DISPLAY bar, not the growing one: a word still on its way in is no cue to itself. */
    @Test
    fun aWordShortOfTheDisplayBarIsNotAsked() {
        assertFalse("window" in ids(standing = mapOf("window" to ScrambleFixture.GROWING)))
    }

    /** A separable or reflexive verb is two spellings and a word order — not this drill's. */
    @Test
    fun aWordTheTargetWritesAsTwoTokensIsNotAsked() {
        assertFalse("wash" in ids())
    }

    /** Below the minimum the anchors give the word away, so it is never asked. */
    @Test
    fun aWordTooShortToAnchorIsNotAsked() {
        assertFalse("clock" in ids())
        assertEquals(WordScrambleAvailability.MIN_LETTERS - 1, "Uhr".length)
    }

    /** A phrase belongs to the other scramble, whatever its length. */
    @Test
    fun aPhraseIsNeverAWordScramble() {
        assertFalse("greeting" in ids())
    }

    /** Suspending a word says stop asking it, and a drill is an asking. */
    @Test
    fun aSuspendedWordIsNotAsked() {
        assertFalse("sun" in ids(suspended = setOf("sun")))
    }

    /**
     * The Sprosse ceiling is read off the POOL, not off the masking ladder: every Sprosse the
     * report names holds words enough to be worth climbing, and the one above it does not.
     */
    @Test
    fun theLadderTopsOutWhereThePoolStopsFillingIt() {
        val report = report()
        for (level in 1..report.maxLevel) {
            assertTrue(
                report.words.count { it.reach >= report.lettersAt(level) } >=
                    WordScrambleAvailability.POOL_FLOOR,
                "Sprosse $level cannot be filled",
            )
        }
        assertTrue(
            report.words.count { it.reach >= report.lettersAt(report.maxLevel + 1) } <
                WordScrambleAvailability.POOL_FLOOR,
            "the ladder stops short of what the pool would carry",
        )
    }

    /** The floor rises a letter a Sprosse, from the shortest word the drill asks at all. */
    @Test
    fun theFloorRisesALetterEachSprosse() {
        val report = report()
        assertEquals(WordScrambleAvailability.MIN_LETTERS, report.lettersAt(1))
        for (level in 1 until report.maxLevel) {
            assertEquals(report.lettersAt(level) + 1, report.lettersAt(level + 1))
        }
    }

    /** The chip predicate is the pool floor, and an empty box offers nothing. */
    @Test
    fun theChipWaitsForAPoolWorthARun() {
        assertTrue(WordScrambleAvailability.drillExists(ScrambleFixture.box(cards)))
        val thin = cards.take(WordScrambleAvailability.POOL_FLOOR - 1)
        assertFalse(WordScrambleAvailability.drillExists(ScrambleFixture.box(thin)))
        assertFalse(WordScrambleAvailability.drillExists(ScrambleFixture.box(emptyList())))
    }
}
