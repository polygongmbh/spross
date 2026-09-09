package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The rules a phrase slot renders by — fraction shape, variant frames, sentence-start
 * casing, note and gloss merging, the frame × slot expansion and the join's shape. The worked examples
 * these rules are drawn from are [PhraseSlotInstantiationTests].
 */
class PhraseSlotTests {

    private fun frame(target: String, slug: String) = RealFrames.frame(target, slug)

    // Fraction slots — the recipe frame, where the fraction is a bare noun

    @Test
    fun germanRecipeFractionDropsInAsANoun() {
        val task = PhraseSlots.instantiate(
            RealFrames.frame("de", "i-need-n-kilo-of-flour", source = "en"), 1L, 4L,
        )
        assertEquals("I need 1/4 of a kilo of flour.", task.prompt)
        assertEquals("Ich brauche ein Viertel Kilo Mehl.", task.display)
        assertEquals(
            listOf("Ich brauche ein Viertel Kilo Mehl.", "Ich brauche 1/4 Kilo Mehl."),
            task.accepted,
        )
        assertEquals(TrainerKind.Fraction, task.kind)
    }

    @Test
    fun englishAndSpanishRecipeFractionsKeepTheirOwnShape() {
        val english = PhraseSlots.instantiate(RealFrames.frame("en", "i-need-n-kilo-of-flour"), 3L, 4L)
        assertEquals("I need three quarters of a kilo of flour.", english.display)
        assertTrue("I need three fourths of a kilo of flour." in english.accepted, english.accepted.toString())
        val spanish = PhraseSlots.instantiate(RealFrames.frame("es", "i-need-n-kilo-of-flour"), 1L, 3L)
        assertEquals("Necesito un tercio de kilo de harina.", spanish.display)
    }

    /**
     * The one rule the slot draw adds over the Forms ladder's: no halves. German and Spanish
     * read 1/2 adjectivally ("ein halb", "medio"), so it would have to agree with the noun
     * beside it — and the only agreement device runs the other way round.
     */
    @Test
    fun aFractionSlotNeverDrawsAHalfAndStaysReduced() {
        val rng = Random(20260807)
        val pattern = Regex("""(\d+)/(\d+)""")
        for (template in RealFrames.all.filter { it.slotKind == TrainerKind.Fraction }) {
            val unitOnly = mutableSetOf<Boolean>()
            for (level in 1..Trainer.maxLevel(TrainerKind.Fraction)) {
                repeat(80) {
                    val task = PhraseSlots.sample(template, level, rng)
                    val (n, d) = pattern.find(task.prompt)!!.destructured
                    val where = "${template.target} L$level: $n/$d"
                    assertTrue(d.toInt() >= 3, where)
                    assertTrue(n.toInt() < d.toInt(), where)
                    assertEquals(1, gcd(n.toInt(), d.toInt()), where)
                    if (level == 1) assertTrue(n == "1" && d.toInt() <= 4, where)
                    if (level == 2) unitOnly += n == "1"
                }
            }
            assertTrue(false in unitOnly, "${template.target}: the top Sprosse never left the unit fractions")
        }
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    // Variant frames

    /** A variant frame is graded, never displayed — one sentence per frame × slot rendering. */
    @Test
    fun variantFrameExpandsIntoAcceptedNeverIntoDisplay() {
        val task = PhraseSlots.instantiate(RealFrames.frame("de", "repeat-please", source = "sw"), value = 7L)
        assertEquals("Wiederholen Sie bitte: sieben.", task.display)
        assertEquals(
            listOf(
                "Wiederholen Sie bitte: sieben.",
                "Wiederholen Sie bitte: 7.",
                "Wiederhole bitte: sieben.",
                "Wiederhole bitte: 7.",
            ),
            task.accepted,
        )
    }

    @Test
    fun syntheticVariantFramesCrossEverySlotRendering() {
        val synthetic = PhraseTemplate(
            id = "test-variants", source = "de", target = "sw",
            sourceTemplate = "Sag {slot}.",
            targetTemplate = "Sema {slot}.",
            slotKind = TrainerKind.Clock,
            acceptedFrames = listOf("Tafadhali sema {slot}."),
        )
        val task = PhraseSlots.instantiate(synthetic, hour = 8, minute = 0)
        assertEquals("Sema saa mbili asubuhi.", task.display)
        assertEquals(
            listOf(
                "Sema saa mbili.", "Sema saa mbili asubuhi.",
                "Sema 08:00.", "Sema 8:00.",
                "Tafadhali sema saa mbili.", "Tafadhali sema saa mbili asubuhi.",
                "Tafadhali sema 08:00.", "Tafadhali sema 8:00.",
            ),
            task.accepted,
        )
    }

    // Casing at sentence start

    /**
     * German readings begin on NOUNS, whose capital is part of the word — the composer
     * lowercased every mid-sentence reading for Swahili's sake and spelled those wrong.
     */
    @Test
    fun germanNounReadingsKeepTheirCapitalMidSentence() {
        val train = RealFrames.frame("de", "train-departs-at", source = "en")
        val midnight = PhraseSlots.instantiate(train, hour = 0, minute = 0)
        assertEquals("Der Zug fährt um Mitternacht ab.", midnight.display)
        assertTrue(midnight.accepted.none { "mitternacht" in it }, midnight.accepted.toString())
        assertEquals(
            "Der Zug fährt um Viertel nach acht ab.",
            PhraseSlots.instantiate(train, hour = 8, minute = 15).display,
        )
        val noon = PhraseSlots.instantiate(RealFrames.frame("de", "it-is-now", source = "uk"), 12, 0)
        assertEquals("Es ist jetzt Mittag.", noon.display)
    }

    @Test
    fun sentenceInitialSlotKeepsCapital() {
        val synthetic = PhraseTemplate(
            id = "test-initial", source = "de", target = "sw",
            sourceTemplate = "{slot} Uhr.",
            targetTemplate = "{slot}, sawa?",
            slotKind = TrainerKind.Clock,
        )
        val task = PhraseSlots.instantiate(synthetic, hour = 20, minute = 0)
        assertEquals("Saa mbili usiku, sawa?", task.display)
    }

    // Note + gloss merging

    @Test
    fun notesMergeTemplateThenSlot() {
        val task = PhraseSlots.instantiate(frame("uk", "alarm-clock-shows"), hour = 2, minute = 15)
        val gloss = task.gloss ?: ""
        assertTrue(gloss.startsWith("wörtl.: „Auf dem Wecker [ist] …“ · "))
        // The frame's note is authored per source language; the slot's names the
        // alternative readings, and does so in the language being answered in.
        assertTrue("також: п'ятнадцять хвилин на третю" in gloss, gloss)
    }

    // Every accepted frame × slot variant → exactly one accepted sentence

    /**
     * Structural sweep over every joined pair, at every clock position and value the drill
     * reaches. Deliberately NOT a second copy of [PhraseSlots.compose] — the exact sentences
     * are pinned by the hand-written examples above, one language at a time. What is asserted
     * here is only what restating the rules could not tell us: nothing repeats, and the
     * sentence the reveal teaches is one of the sentences it grades.
     */
    @Test
    fun everyPairAssemblesDistinctAnswersTheDisplayBelongsTo() {
        for (template in RealFrames.all) {
            val tasks = when (template.slotKind) {
                TrainerKind.Clock ->
                    listOf(0, 6, 9, 13, 14, 20, 23).flatMap { h ->
                        listOf(0, 15, 20, 30, 35, 45, 55).map { m ->
                            PhraseSlots.instantiate(template, hour = h, minute = m)
                        }
                    }
                TrainerKind.Fraction ->
                    listOf(1L to 3L, 1L to 4L, 2L to 3L, 3L to 4L, 5L to 12L)
                        .map { (n, d) -> PhraseSlots.instantiate(template, n, d) }
                else ->
                    listOf(1L, 2L, 5L, 11L, 21L, 22L, 25L, 100L, 347L, 1000L, 1978L, 2026L)
                        .map { PhraseSlots.instantiate(template, value = it) }
            }
            for (task in tasks) {
                val where = "${template.source}→${template.target} ${template.id}"
                assertTrue(task.accepted.isNotEmpty(), where)
                assertEquals(task.accepted.size, task.accepted.toSet().size, "$where: duplicates")
                assertTrue(task.display in task.accepted, "$where: ${task.display}")
            }
        }
    }

    @Test
    fun samplingIsDeterministicAndMatchesInstantiate() {
        for (template in RealFrames.all) {
            val a = Random(0xC0FFEE)
            val b = Random(0xC0FFEE)
            repeat(50) {
                val sampled = PhraseSlots.sample(template, a)
                // Reconstruct with the same-seeded RNG draws, through the Trainer's own
                // sampler — an independent path to the value the composition used.
                val slot = Trainer.sample(template.slotKind, template.target, b)
                val expected = when (template.slotKind) {
                    TrainerKind.Clock -> {
                        val parts = slot.prompt.split(":").map { it.toInt() }
                        PhraseSlots.instantiate(template, hour = parts[0], minute = parts[1])
                    }
                    TrainerKind.Fraction -> {
                        val parts = slot.prompt.split("/").map { it.toLong() }
                        PhraseSlots.instantiate(template, parts[0], parts[1])
                    }
                    else -> PhraseSlots.instantiate(template, value = slot.prompt.toLong())
                }
                assertEquals(expected, sampled, template.id)
            }
        }
    }

    // Join shape (per-file structural rules live in CatalogFrameLintTest)

    @Test
    fun theJoinIsSymmetricAndPerPairUnique() {
        assertTrue(RealFrames.of("de", "sw").size >= 10)
        assertTrue(RealFrames.of("de", "uk").size >= 10)
        // Authoring a language lights up both directions with the same frame set.
        assertEquals(RealFrames.of("de", "sw").map { it.id }, RealFrames.of("sw", "de").map { it.id })
        for (template in RealFrames.all) {
            assertTrue(template.source != template.target, template.id)
            assertTrue(template.sourceTemplate != template.targetTemplate, template.id)
        }
        for ((source, target) in RealFrames.all.map { it.source to it.target }.distinct()) {
            val ids = RealFrames.of(source, target).map { it.id }
            assertEquals(ids.size, ids.toSet().size, "duplicate frame id in $source→$target")
        }
    }

    /**
     * The Forms seal, checked where a frame is BUILT rather than where it is drawn:
     * a number form has no phrase generator, and a template carrying one would only
     * blow up at the first draw, in a run, with a catalog long since shipped.
     */
    @Test
    fun aFormsSlotIsRejectedWhenTheTemplateIsBuilt() {
        assertFailsWith<IllegalArgumentException> {
            PhraseTemplate(
                id = "n-th-place", source = "de", target = "en",
                sourceTemplate = "Ich bin auf Platz {slot}.",
                targetTemplate = "I am in {slot} place.",
                slotKind = TrainerKind.Forms,
            )
        }
    }

    /** `copy` runs the same init, so the seal holds however a template is derived. */
    @Test
    fun aFormsSlotIsRejectedWhenATemplateIsCopiedIntoOne() {
        assertFailsWith<IllegalArgumentException> {
            RealFrames.frame("sw", "we-have-n-plates").copy(slotKind = TrainerKind.Forms)
        }
    }

    /**
     * No marker ever reaches the learner. Agreement is authored per realization, so the
     * language supplying the PROMPT fills its own `{count}` — uk authors it on three frames,
     * which used to surface literally the moment Ukrainian became a source (uk→en, uk→sw).
     */
    @Test
    fun noMarkerSurvivesIntoAnythingTheLearnerSees() {
        val rng = Random(20260802)
        for (template in RealFrames.all) {
            // 21 and 13 straddle the Slavic agreement split (one / many).
            val tasks = listOf(21L, 13L).map { RealFrames.instantiate(template, it, hour = 21) } +
                listOf(PhraseSlots.sample(template, rng))

            for (task in tasks) {
                for (surface in listOf(task.prompt, task.display) + task.accepted) {
                    for (marker in listOf(PhraseTemplate.SLOT_MARKER, PhraseTemplate.COUNT_MARKER)) {
                        assertTrue(
                            marker !in surface,
                            "${template.source}→${template.target} ${template.id}: „$marker“ reached the learner in „$surface“",
                        )
                    }
                }
            }
        }
    }
}
