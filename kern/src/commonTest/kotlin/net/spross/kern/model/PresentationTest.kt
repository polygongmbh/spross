package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.Box

/**
 * Presentation resolution: FNV-1a hash bit-exactness, first-exposure rule,
 * parity alternation, synonym rotation, emoji policy.
 */
class PresentationTest {

    private val produce = PresentationRole.Produce
    private val recognize = PresentationRole.Recognize

    // Vectors derived from v1's Swift `BoxEngine.stableHash` (FNV-1a 64-bit over
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
            assertEquals(recognize, presentationRole(id, 0), "card: $id")
        }
    }

    @Test
    fun secondReviewIsAlwaysProduction() {
        // Ruling 2026-07-22: after the teaching first exposure, the card returns
        // as production — for BOTH hash parities.
        for (id in listOf("w01", "w02", "kitchen/fridge", "alpha/mouse", "тест")) {
            assertEquals(produce, presentationRole(id, 1), "card: $id")
        }
    }

    @Test
    fun rolesAlternateByReviewParityWithPerCardHashOffset() {
        // w01 (hash odd): recognition at even counts — a clean R/P alternation.
        assertEquals(
            listOf(recognize, produce, recognize, produce, recognize, produce),
            (0..5).map { presentationRole("w01", it) },
        )
        // w02 (hash even): count 0 forced recognition, count 1 forced production;
        // from count 2 the parity rule recognizes at odd counts.
        assertEquals(
            listOf(recognize, produce, produce, recognize, produce, recognize),
            (0..5).map { presentationRole("w02", it) },
        )
    }

    // -- synonym rotation --------------------------------------------------------------

    @Test
    fun firstExposurePromptsTheCanonicalForm() {
        val card = Box.word(1, synonyms = listOf("s1a", "s1b"))
        assertEquals("t1", recognitionPromptForm(card, 0))
    }

    @Test
    fun singleFormCardsAlwaysPromptTheirText() {
        val card = Box.word(2)
        for (count in 0..8) {
            assertEquals("t2", recognitionPromptForm(card, count))
        }
    }

    // A card with 2 synonyms prompts all 3 forms within 6 recognition reviews —
    // every form gets prompted at zero extra scheduling cost.
    @Test
    fun rotationCoversAllFormsWithinSixRecognitionReviews() {
        for (n in 1..4) { // several ids → both hash parities and offsets
            val card = Box.word(n, synonyms = listOf("s${n}a", "s${n}b"))
            val prompted = mutableSetOf<String>()
            var recognitions = 0
            var count = 0
            while (recognitions < 6) {
                if (presentationRole(card.id, count) == recognize) {
                    prompted += recognitionPromptForm(card, count)
                    recognitions += 1
                }
                count += 1
            }
            assertEquals(setOf("t$n", "s${n}a", "s${n}b"), prompted, "card: ${card.id}")
        }
    }

    @Test
    fun rotationIsDeterministicAndNeverUsesVariants() {
        val card = Box.word(3, synonyms = listOf("s3a"), variants = listOf("v3"))
        val forms = (0..12).map { recognitionPromptForm(card, it) }
        assertEquals(forms, (0..12).map { recognitionPromptForm(card, it) })
        assertTrue(forms.all { it in setOf("t3", "s3a") })
    }

    // -- emoji policy ------------------------------------------------------------------

    @Test
    fun emojiRidesThePromptOnlyWhereItCannotGiveTheAnswerAway() {
        // First exposure: on the prompt — the cue that makes a first attempt possible.
        assertEquals(EmojiCue.Upfront, emojiCue(recognize, settled = false, reviewCount = 0))
        // Produce while the word is still landing: the source prompt already names
        // the concept, so the picture adds support without leaking anything.
        assertEquals(EmojiCue.Upfront, emojiCue(produce, settled = false, reviewCount = 1))
    }

    @Test
    fun emojiRidesTheRevealEverywhereElse() {
        // Recognition measurement reviews: the picture depicts the answer, so it
        // waits for the reveal rather than disappearing.
        assertEquals(EmojiCue.OnReveal, emojiCue(recognize, settled = false, reviewCount = 1))
        assertEquals(EmojiCue.OnReveal, emojiCue(recognize, settled = true, reviewCount = 4))
        // Settled words get no prompt support in either role — but still bind on reveal.
        assertEquals(EmojiCue.OnReveal, emojiCue(produce, settled = true, reviewCount = 4))
        assertEquals(EmojiCue.OnReveal, emojiCue(produce, settled = true, reviewCount = 6))
    }
}
