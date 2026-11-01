package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.spross.kern.box.Box

/**
 * Presentation resolution: FNV-1a hash bit-exactness, first-exposure rule,
 * the role cycle, synonym rotation, emoji policy.
 */
class PresentationTest {

    private val produce = PresentationRole.Produce
    private val recognize = PresentationRole.Recognize
    private val hear = PresentationRole.Hear

    private fun role(id: String, count: Int, arrived: Boolean = false, audible: Boolean = true) =
        presentationRole(id, count, arrived, audible)

    // Reference vectors for FNV-1a 64-bit over
    // UTF-8: offset 0xcbf29ce484222325, prime 0x100000001b3, wrapping multiply);
    // "kitchen/fridge", "alpha/mouse", and "тест" cross-checked independently in
    // python3 — bit-exactness keeps alternation parity stable across platforms.
    @Test
    fun fnv1a64MatchesV1ReferenceVectors() {
        assertEquals(0xcbf29ce484222325uL, fnv1a64(""))
        assertEquals(0xaf63dc4c8601ec8cuL, fnv1a64("a"))
        assertEquals(0xd1445569ed7459b1uL, fnv1a64("kitchen/fridge"))
        assertEquals(0x2e8215459e83409fuL, fnv1a64("alpha/mouse"))
        assertEquals(0x0ce43a5ff22a95c7uL, fnv1a64("school/teacher-f"))
        assertEquals(0x6af597f8cd7d77ccuL, fnv1a64("тест")) // UTF-8 multibyte
        assertEquals(0x5f4e221949054339uL, fnv1a64("w01")) // parity 1
        assertEquals(0x5f4e1f1949053e20uL, fnv1a64("w02")) // parity 0
    }

    @Test
    fun firstExposureIsAlwaysRecognition() {
        for (id in listOf("w01", "w02", "kitchen/fridge", "alpha/mouse", "тест")) {
            assertEquals(recognize, role(id, 0, arrived = true), "card: $id")
        }
    }

    @Test
    fun secondReviewIsAlwaysProduction() {
        // Ruling 2026-07-22: after the teaching first exposure, the card returns
        // as production — for BOTH hash parities.
        for (id in listOf("w01", "w02", "kitchen/fridge", "alpha/mouse", "тест")) {
            assertEquals(produce, role(id, 1, arrived = true), "card: $id")
        }
    }

    @Test
    fun aWordStillLandingAlternatesRecognitionAndProduction() {
        // w01 (hash odd): recognition at even counts — a clean R/P alternation.
        assertEquals(
            listOf(recognize, produce, recognize, produce, recognize, produce),
            (0..5).map { role("w01", it) },
        )
        // w02 (hash even): count 0 forced recognition, count 1 forced production;
        // from count 2 the parity rule recognizes at odd counts.
        assertEquals(
            listOf(recognize, produce, produce, recognize, produce, recognize),
            (0..5).map { role("w02", it) },
        )
    }

    @Test
    fun anArrivedWordCyclesRecognitionProductionAndHearing() {
        for (id in listOf("w01", "w02", "kitchen/fridge", "тест")) {
            for (window in (2..14).map { role(id, it, arrived = true) }.windowed(3)) {
                assertEquals(setOf(recognize, produce, hear), window.toSet(), "card: $id")
            }
        }
    }

    @Test
    fun aSilentDeviceAsksBySightWhereItWouldHaveAskedByEar() {
        for (id in listOf("w01", "w02", "kitchen/fridge", "тест")) {
            for (count in 0..14) {
                val heard = role(id, count, arrived = true)
                val silent = role(id, count, arrived = true, audible = false)
                assertEquals(if (heard == hear) recognize else heard, silent, "card: $id, count $count")
            }
        }
    }

    // -- synonym rotation --------------------------------------------------------------

    @Test
    fun firstExposurePromptsTheCanonicalForm() {
        val card = Box.word(1, teaches = listOf("s1a", "s1b"))
        assertEquals("t1", recognitionPromptForm(card, 0, arrived = false))
    }

    @Test
    fun singleFormCardsAlwaysPromptTheirText() {
        val card = Box.word(2)
        for (count in 0..8) {
            assertEquals("t2", recognitionPromptForm(card, count, arrived = false))
        }
    }

    // A card with 2 `teaches` prompts all 3 forms within 6 recognition reviews,
    // in either cycle — every form gets prompted at zero extra scheduling cost.
    @Test
    fun rotationCoversAllFormsWithinSixRecognitionReviews() {
        for (arrived in listOf(false, true)) {
            for (n in 1..4) { // several ids → every hash offset
                val card = Box.word(n, teaches = listOf("s${n}a", "s${n}b"))
                val prompted = (2..40)
                    .filter { role(card.id, it, arrived) == recognize }
                    .take(6)
                    .map { recognitionPromptForm(card, it, arrived) }
                assertEquals(setOf("t$n", "s${n}a", "s${n}b"), prompted.toSet(), "card: ${card.id}, arrived $arrived")
            }
        }
    }

    @Test
    fun rotationIsDeterministicAndNeverUsesAccepts() {
        val card = Box.word(3, teaches = listOf("s3a"), accepts = listOf("v3"))
        val forms = (0..12).map { recognitionPromptForm(card, it, arrived = true) }
        assertEquals(forms, (0..12).map { recognitionPromptForm(card, it, arrived = true) })
        assertTrue(forms.all { it in setOf("t3", "s3a") })
    }

    // -- emoji policy ------------------------------------------------------------------

    @Test
    fun emojiRidesThePromptOnlyWhereItCannotGiveTheAnswerAway() {
        // Produce while the word is still landing: the source prompt already names
        // the concept, so the picture adds support without leaking anything. This is
        // the ONLY prompt it rides, and by role resolution the second review is one.
        assertEquals(EmojiCue.Upfront, emojiCue(produce, arrived = false))
    }

    @Test
    fun emojiRidesTheRevealEverywhereElse() {
        // Every recognition prompt, the FIRST EXPOSURE included: the picture depicts
        // the very thing being asked for, and a self-grade cannot tell "I knew the
        // word" from "the picture was obvious". It waits for the reveal, which is
        // where a first sight teaches the word anyway.
        assertEquals(EmojiCue.OnReveal, emojiCue(recognize, arrived = false))
        assertEquals(EmojiCue.OnReveal, emojiCue(recognize, arrived = true))
        // A word that has landed gets no prompt support in either role — but still binds on reveal.
        assertEquals(EmojiCue.OnReveal, emojiCue(produce, arrived = true))
    }

    // -- pronunciation policy ----------------------------------------------------------

    @Test
    fun theTargetIsSpokenOnlyOnceItsFormIsOnScreen() {
        // Recognition prompts the target itself: hearing it from frame one teaches.
        assertEquals(PronunciationCue.Upfront, pronunciationCue(recognize))
        // Production asks for that very form — speaking it early would hand it over.
        assertEquals(PronunciationCue.OnReveal, pronunciationCue(produce))
        // On a hear turn the sound IS the prompt; holding it back would ask nothing at all.
        assertEquals(PronunciationCue.Upfront, pronunciationCue(hear))
    }

    @Test
    fun theMeaningIsSpokenWhereverTheTargetIsNot() {
        for (role in PresentationRole.entries) {
            assertNotEquals(pronunciationCue(role), meaningCue(role))
        }
    }

    // -- typed or recalled -------------------------------------------------------------

    @Test
    fun aSettledWordIsTypedEveryOtherTurnOfARoleAndAnUnsettledOneAlways() {
        for (id in listOf("w01", "w02", "kitchen/fridge", "тест")) {
            assertTrue((0..20).all { turnAnswer(id, it, settled = false) == TurnAnswer.Typed })
            for (asked in listOf(produce, hear)) {
                val settled = (2..30).filter { role(id, it, arrived = true) == asked }.map { turnAnswer(id, it, settled = true) }
                for (pair in settled.windowed(2)) {
                    assertTrue(pair.toSet().size == 2, "$id does not alternate typing and recall on $asked: $settled")
                }
            }
        }
    }
}
