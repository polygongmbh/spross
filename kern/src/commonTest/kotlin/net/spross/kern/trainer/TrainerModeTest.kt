package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The run SPEC: what a selection is filed under, what it may ask, which picks may combine, and
 * what one draw spends its randomness on. The ladder itself (`DrillUnlocks`, `DrillRamp`) is
 * pinned by `DrillProgressionTests`; what the answers do to it is `TrainerRunTest`.
 */
class TrainerModeTest {

    private fun frame(kind: TrainerKind) = PhraseTemplate(
        id = "frame-$kind",
        source = "de",
        target = "uk",
        sourceTemplate = "Um {slot}.",
        targetTemplate = "О {slot}.",
        slotKind = kind,
    )

    private fun numbers(language: String = "de") = TrainerMode(DrillVariant.Numbers, language)

    // MARK: - What a run is filed under

    /**
     * Byte-for-byte what the app already stored: Kotlin's own spelling for the slot variants,
     * the lowercase word for Phrases, modifier tags in ladder order.
     */
    @Test
    fun aRunIsFiledUnderItsWholeSelectionAndHowItWasPlayed() {
        assertEquals("Numbers.sw", numbers("sw").recordKey)
        assertEquals("trainer.record.", TrainerMode.RECORD_PREFIX)
        assertEquals("trainer.level.", TrainerMode.PROGRESS_PREFIX)

        val mixed = TrainerMode(
            listOf(DrillVariant.Numbers, DrillVariant.Clock),
            "de",
            setOf(DrillModifier.Fast, DrillModifier.Reverse),
        )
        assertEquals("Numbers+Clock.rev.fast.de", mixed.recordKey)
        // The set's iteration order may not reach the key.
        assertEquals(
            mixed.recordKey,
            TrainerMode(
                listOf(DrillVariant.Numbers, DrillVariant.Clock),
                "de",
                setOf(DrillModifier.Reverse, DrillModifier.Fast),
            ).recordKey,
        )
    }

    /** The two typed drills' extra files: how many answers one run gave, and which Sprossen it answered out. */
    @Test
    fun theAnsweredOutSprossenAreFiledAsAMaskPerDirection() {
        assertEquals("trainer.answers.", TrainerMode.ANSWERS_PREFIX)
        assertEquals("trainer.cleared.", TrainerMode.CLEARED_PREFIX)
        assertEquals(".rev", TrainerMode.REVERSED_SUFFIX)
        assertEquals("countries.de-sw", TrainerMode.clearedKey("countries.de-sw", reverse = false))
        assertEquals("countries.de-sw.rev", TrainerMode.clearedKey("countries.de-sw", reverse = true))
        assertEquals(0b101, TrainerMode.clearedMask(setOf(1, 3)))
        assertEquals(setOf(1, 3), TrainerMode.clearedSprossen(0b101))
        assertEquals(setOf(9), TrainerMode.clearedSprossen(TrainerMode.clearedMask(setOf(9))))
        assertEquals(emptySet(), TrainerMode.clearedSprossen(0))
    }

    /** A run opens on the lowest Sprosse not yet answered out — a gap is where it opens, and a ladder answered out opens on its top. */
    @Test
    fun aRunOpensOnTheLowestSprosseNotAnsweredOut() {
        assertEquals(1, TrainerMode.entrySprosse(emptySet(), 9))
        assertEquals(3, TrainerMode.entrySprosse(setOf(1, 2), 9))
        assertEquals(2, TrainerMode.entrySprosse(setOf(1, 3), 9))
        assertEquals(9, TrainerMode.entrySprosse((1..9).toSet(), 9))
        assertEquals(5, TrainerMode.entrySprosse((1..9).toSet(), 5), "clamped to the ladder as it stands")
    }

    /** A Sprosse belongs to ONE variant, which is what lets the ladder read them all at once. */
    @Test
    fun aSprosseIsFiledPerVariantAndPhrasesKeepsItsLowercaseSpelling() {
        assertEquals("Numbers.sw", TrainerMode.progressKey(DrillVariant.Numbers, "sw"))
        assertEquals("Clock.sw", TrainerMode.progressKey(DrillVariant.Clock, "sw"))
        assertEquals("Forms.sw", TrainerMode.progressKey(DrillVariant.Forms, "sw"))
        assertEquals("phrases.sw", TrainerMode.progressKey(DrillVariant.Phrases, "sw"))
        assertEquals("Numbers.sw", numbers("sw").progressKey(DrillVariant.Numbers))
    }

    /**
     * A sentence record is kept per PAIR — and, quirk carried over verbatim, so is every other
     * record of a run the overview handed a phrase source, because it hands one over whenever
     * the pair realizes frames at all. `Numbers.de-uk`, not `Numbers.uk`.
     */
    @Test
    fun aPhraseSourceSuffixesTheRecordLanguageEvenWhereTheRunAsksNoSentence() {
        val templates = listOf(frame(TrainerKind.Clock))
        val sentences = TrainerMode(listOf(DrillVariant.Phrases), "uk", "de", templates, emptySet())
        assertEquals("phrases.de-uk", sentences.recordKey)

        val counting = TrainerMode(listOf(DrillVariant.Numbers), "uk", "de", templates, emptySet())
        assertEquals("Numbers.de-uk", counting.recordKey)
        // The Sprosse key never takes the pair — a Sprosse belongs to the language it was climbed in.
        assertEquals("Numbers.uk", counting.progressKey(DrillVariant.Numbers))
    }

    // MARK: - What a run may ask

    @Test
    fun aFramelessSentencePickIsDroppedAndAnEmptyRunFallsBackToCounting() {
        val frameless = TrainerMode(listOf(DrillVariant.Phrases), "de", "de", emptyList(), emptySet())
        assertEquals(listOf(DrillVariant.Numbers), frameless.variants)
        val mixed = TrainerMode(
            listOf(DrillVariant.Numbers, DrillVariant.Phrases),
            "de",
            "de",
            emptyList(),
            emptySet(),
        )
        assertEquals(listOf(DrillVariant.Numbers), mixed.variants)
        assertEquals(
            listOf(DrillVariant.Phrases),
            TrainerMode(
                listOf(DrillVariant.Phrases), "uk", "de", listOf(frame(TrainerKind.Clock)), emptySet(),
            ).variants,
        )
    }

    /** Each variant ramps to kern's own ceiling; a sentence run to the highest its frames carry. */
    @Test
    fun aVariantRampsToItsOwnCeiling() {
        val mode = TrainerMode(
            listOf(DrillVariant.Phrases),
            "uk",
            "de",
            listOf(frame(TrainerKind.Years), frame(TrainerKind.Clock)),
            emptySet(),
        )
        assertEquals(Trainer.maxLevel(TrainerKind.Numbers), mode.maxLevel(DrillVariant.Numbers))
        assertEquals(Trainer.maxLevel(TrainerKind.Clock), mode.maxLevel(DrillVariant.Clock))
        assertEquals(Trainer.maxLevel(TrainerKind.Forms), mode.maxLevel(DrillVariant.Forms))
        // Years tops out at 3, the clock at 5 — the run takes the higher of the two frames.
        assertEquals(Trainer.maxLevel(TrainerKind.Clock), mode.maxLevel(DrillVariant.Phrases))
    }

    /** A padlock that can never open is a lie: an unrealizable variant has no row at all. */
    @Test
    fun onlyWhatThePairCanAskIsOffered() {
        assertEquals(
            listOf(DrillVariant.Numbers, DrillVariant.Clock, DrillVariant.Forms),
            DrillSelection.offered("de", phrasesRealized = false),
        )
        assertEquals(
            listOf(DrillVariant.Numbers, DrillVariant.Clock, DrillVariant.Phrases, DrillVariant.Forms),
            DrillSelection.offered("de", phrasesRealized = true),
        )
    }

    /**
     * Combining exercises is itself earned: while any offered row is still locked the picks are
     * a radio that never empties, and only a fully open ladder turns them into checkboxes.
     */
    @Test
    fun picksCombineOnlyOnceEveryOfferedRowIsOpen() {
        val offered = DrillSelection.offered("de", phrasesRealized = false)
        val fresh = emptyMap<DrillVariant, Int>()
        assertFalse(DrillSelection.combining(offered, fresh))
        val climbed = mapOf(DrillVariant.Numbers to 7)
        assertTrue(DrillSelection.combining(offered, climbed))

        // Locked: the tap replaces the pick, and tapping the chosen row leaves it chosen.
        assertEquals(
            listOf(DrillVariant.Clock),
            DrillSelection.toggled(listOf(DrillVariant.Numbers), DrillVariant.Clock, combining = false),
        )
        assertEquals(
            listOf(DrillVariant.Numbers),
            DrillSelection.toggled(listOf(DrillVariant.Numbers), DrillVariant.Numbers, combining = false),
        )
        // Open: the tap toggles, and the ladder's order decides how the picks read back.
        assertEquals(
            listOf(DrillVariant.Numbers, DrillVariant.Clock),
            DrillSelection.toggled(listOf(DrillVariant.Clock), DrillVariant.Numbers, combining = true),
        )
        assertEquals(
            listOf(DrillVariant.Clock),
            DrillSelection.toggled(
                listOf(DrillVariant.Numbers, DrillVariant.Clock),
                DrillVariant.Numbers,
                combining = true,
            ),
        )
    }

    @Test
    fun thePicksFollowTheLadderTheRunJustMoved() {
        val offered = DrillSelection.offered("de", phrasesRealized = false)
        val fresh = emptyMap<DrillVariant, Int>()
        // A locked pick is dropped, and what survives collapses to one while the list is a radio.
        assertEquals(
            listOf(DrillVariant.Numbers),
            DrillSelection.normalized(listOf(DrillVariant.Numbers, DrillVariant.Clock), offered, fresh),
        )
        // Nothing picked and the ladder closed still opens on the one row that is free.
        assertEquals(listOf(DrillVariant.Numbers), DrillSelection.normalized(emptyList(), offered, fresh))
        // A Sprosse the run just booked lets both stand.
        assertEquals(
            listOf(DrillVariant.Numbers, DrillVariant.Clock),
            DrillSelection.normalized(
                listOf(DrillVariant.Clock, DrillVariant.Numbers),
                offered,
                mapOf(DrillVariant.Numbers to 7),
            ),
        )
    }

    @Test
    fun mixFlipsPerTaskWhileReverseHoldsOneDirection() {
        val rng = Random(21)
        val plain = TrainerMode(DrillVariant.Numbers, "de")
        val reverse = TrainerMode(listOf(DrillVariant.Numbers), "de", setOf(DrillModifier.Reverse))
        val mix = TrainerMode(listOf(DrillVariant.Numbers), "de", setOf(DrillModifier.Mix))
        val levels = mapOf(DrillVariant.Numbers to 3)

        fun TrainerMode.reversedDraw() = assertNotNull(draw(levels, null, emptySet(), rng).drawn).reversed
        assertTrue((1..20).none { plain.reversedDraw() })
        assertTrue((1..20).all { reverse.reversedDraw() })
        val flips = (1..40).map { mix.reversedDraw() }
        assertTrue(flips.contains(true) && flips.contains(false), "Mix flips per task: $flips")
    }

    /** Mix widens Forms out of the Numbers Sprosse — which means nothing without a Numbers Sprosse. */
    @Test
    fun mixWidensFormsOnlyWhileTheRunIsClimbingNumbers() {
        val both = TrainerMode(
            listOf(DrillVariant.Numbers, DrillVariant.Forms), "de", setOf(DrillModifier.Mix),
        )
        assertTrue(both.mixesForms)
        assertFalse(TrainerMode(listOf(DrillVariant.Forms), "de", setOf(DrillModifier.Mix)).mixesForms)
        val unmixed = TrainerMode(listOf(DrillVariant.Numbers, DrillVariant.Forms), "de", emptySet())
        assertFalse(unmixed.mixesForms)
    }

    @Test
    fun fastHalvesTheSprosse() {
        assertEquals(2, TrainerMode(DrillVariant.Numbers, "de").winsToAdvance)
        val fast = TrainerMode(listOf(DrillVariant.Numbers), "de", setOf(DrillModifier.Fast))
        assertEquals(1, fast.winsToAdvance)
    }
}
