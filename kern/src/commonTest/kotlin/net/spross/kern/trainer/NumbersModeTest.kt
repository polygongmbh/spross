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
 * pinned by `DrillProgressionTests`; what the answers do to it is `NumbersRunTest`.
 */
class NumbersModeTest {

    private fun frame(kind: NumbersReading) = PhraseTemplate(
        id = "frame-$kind",
        source = "de",
        target = "uk",
        sourceTemplate = "Um {slot}.",
        targetTemplate = "О {slot}.",
        slotKind = kind,
    )

    private fun numbers(language: String = "de") = NumbersMode(NumbersExercise.Counting, language)

    // MARK: - What a run is filed under

    /**
     * The exercise names in ladder order, then the modifier tags, then the language.
     */
    @Test
    fun aRunIsFiledUnderItsWholeSelectionAndHowItWasPlayed() {
        assertEquals("Counting.sw", numbers("sw").recordKey)

        val mixed = NumbersMode(
            listOf(NumbersExercise.Counting, NumbersExercise.Clock),
            "de",
            setOf(DrillModifier.Fast, DrillModifier.Reverse),
        )
        assertEquals("Counting+Clock.rev.fast.de", mixed.recordKey)
        // The set's iteration order may not reach the key.
        assertEquals(
            mixed.recordKey,
            NumbersMode(
                listOf(NumbersExercise.Counting, NumbersExercise.Clock),
                "de",
                setOf(DrillModifier.Reverse, DrillModifier.Fast),
            ).recordKey,
        )
    }

    /** The two typed drills' extra files: how many answers one run gave, and which Sprossen it answered out. */
    @Test
    fun theAnsweredOutSprossenAreFiledAsAMaskPerDirection() {
        assertEquals("countries.de-sw", NumbersMode.clearedKey("countries.de-sw", reverse = false))
        assertEquals("countries.de-sw.rev", NumbersMode.clearedKey("countries.de-sw", reverse = true))
        assertEquals(setOf(1, 3, 9), NumbersMode.clearedSprossen(NumbersMode.clearedMask(setOf(1, 3, 9))))
        assertEquals(emptySet(), NumbersMode.clearedSprossen(0))
    }

    /** A run opens on the lowest Sprosse not yet answered out — a gap is where it opens, and a ladder answered out opens on its top. */
    @Test
    fun aRunOpensOnTheLowestSprosseNotAnsweredOut() {
        assertEquals(1, NumbersMode.entrySprosse(emptySet(), 9))
        assertEquals(2, NumbersMode.entrySprosse(setOf(1, 3), 9))
        assertEquals(9, NumbersMode.entrySprosse((1..9).toSet(), 9))
        assertEquals(5, NumbersMode.entrySprosse((1..9).toSet(), 5), "clamped to the ladder as it stands")
    }

    /** A tap opens a run only where the learner has been: the entry or below, or a Sprosse reached. */
    @Test
    fun aSprosseNeverReachedCannotBeOpenedByATap() {
        assertTrue(NumbersMode.openable(1, emptySet(), 0, 9))
        assertFalse(NumbersMode.openable(2, emptySet(), 0, 9))
        assertTrue(NumbersMode.openable(3, setOf(1, 2), 0, 9), "the entry")
        assertTrue(NumbersMode.openable(4, setOf(1, 2), 4, 9), "reached by climbing")
        assertFalse(NumbersMode.openable(5, setOf(1, 2), 4, 9))
        assertFalse(NumbersMode.openable(0, setOf(1), 4, 9))
    }

    /** A Sprosse belongs to ONE exercise, which is what lets the ladder read them all at once. */
    @Test
    fun aSprosseIsFiledPerExerciseUnderItsCaseName() {
        assertEquals("Clock.sw", NumbersMode.progressKey(NumbersExercise.Clock, "sw"))
        assertEquals("Counting.sw", numbers("sw").progressKey(NumbersExercise.Counting))
    }

    /**
     * A sentence record is kept per PAIR — and, quirk carried over verbatim, so is every other
     * record of a run the overview handed a phrase source, because it hands one over whenever
     * the pair realizes frames at all. `Counting.de-uk`, not `Counting.uk`.
     */
    @Test
    fun aPhraseSourceSuffixesTheRecordLanguageEvenWhereTheRunAsksNoSentence() {
        val templates = listOf(frame(NumbersReading.Clock))
        val sentences = NumbersMode(listOf(NumbersExercise.Phrases), "uk", "de", templates, emptySet())
        assertEquals("Phrases.de-uk", sentences.recordKey)

        val counting = NumbersMode(listOf(NumbersExercise.Counting), "uk", "de", templates, emptySet())
        assertEquals("Counting.de-uk", counting.recordKey)
        // The Sprosse key never takes the pair — a Sprosse belongs to the language it was climbed in.
        assertEquals("Counting.uk", counting.progressKey(NumbersExercise.Counting))
    }

    // MARK: - What a run may ask

    @Test
    fun aFramelessSentencePickIsDroppedAndAnEmptyRunFallsBackToCounting() {
        val frameless = NumbersMode(listOf(NumbersExercise.Phrases), "de", "de", emptyList(), emptySet())
        assertEquals(listOf(NumbersExercise.Counting), frameless.exercises)
        val mixed = NumbersMode(
            listOf(NumbersExercise.Counting, NumbersExercise.Phrases),
            "de",
            "de",
            emptyList(),
            emptySet(),
        )
        assertEquals(listOf(NumbersExercise.Counting), mixed.exercises)
        assertEquals(
            listOf(NumbersExercise.Phrases),
            NumbersMode(
                listOf(NumbersExercise.Phrases), "uk", "de", listOf(frame(NumbersReading.Clock)), emptySet(),
            ).exercises,
        )
    }

    /** Each exercise ramps to kern's own ceiling; a sentence run to the highest its frames carry. */
    @Test
    fun anExerciseRampsToItsOwnCeiling() {
        val mode = NumbersMode(
            listOf(NumbersExercise.Phrases),
            "uk",
            "de",
            listOf(frame(NumbersReading.Year), frame(NumbersReading.Clock)),
            emptySet(),
        )
        assertEquals(Numbers.maxSprosse(NumbersReading.Cardinal), mode.maxSprosse(NumbersExercise.Counting))
        assertEquals(Numbers.maxSprosse(NumbersReading.Clock), mode.maxSprosse(NumbersExercise.Clock))
        assertEquals(Numbers.maxSprosse(NumbersReading.Form), mode.maxSprosse(NumbersExercise.Forms))
        // Years tops out at 3, the clock at 5 — the run takes the higher of the two frames.
        assertEquals(Numbers.maxSprosse(NumbersReading.Clock), mode.maxSprosse(NumbersExercise.Phrases))
    }

    /** A padlock that can never open is a lie: an unrealizable exercise has no row at all. */
    @Test
    fun onlyWhatThePairCanAskIsOffered() {
        assertEquals(
            listOf(NumbersExercise.Counting, NumbersExercise.Clock, NumbersExercise.Forms),
            DrillSelection.offered("de", phrasesRealized = false),
        )
        assertEquals(
            listOf(NumbersExercise.Counting, NumbersExercise.Clock, NumbersExercise.Phrases, NumbersExercise.Forms),
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
        val fresh = emptyMap<NumbersExercise, Int>()
        assertFalse(DrillSelection.combining(offered, fresh))
        val climbed = mapOf(NumbersExercise.Counting to 7)
        assertTrue(DrillSelection.combining(offered, climbed))

        // Locked: the tap replaces the pick, and tapping the chosen row leaves it chosen.
        assertEquals(
            listOf(NumbersExercise.Clock),
            DrillSelection.toggled(listOf(NumbersExercise.Counting), NumbersExercise.Clock, combining = false),
        )
        assertEquals(
            listOf(NumbersExercise.Counting),
            DrillSelection.toggled(listOf(NumbersExercise.Counting), NumbersExercise.Counting, combining = false),
        )
        // Open: the tap toggles, and the ladder's order decides how the picks read back.
        assertEquals(
            listOf(NumbersExercise.Counting, NumbersExercise.Clock),
            DrillSelection.toggled(listOf(NumbersExercise.Clock), NumbersExercise.Counting, combining = true),
        )
        assertEquals(
            listOf(NumbersExercise.Clock),
            DrillSelection.toggled(
                listOf(NumbersExercise.Counting, NumbersExercise.Clock),
                NumbersExercise.Counting,
                combining = true,
            ),
        )
    }

    @Test
    fun thePicksFollowTheLadderTheRunJustMoved() {
        val offered = DrillSelection.offered("de", phrasesRealized = false)
        val fresh = emptyMap<NumbersExercise, Int>()
        // A locked pick is dropped, and what survives collapses to one while the list is a radio.
        assertEquals(
            listOf(NumbersExercise.Counting),
            DrillSelection.normalized(listOf(NumbersExercise.Counting, NumbersExercise.Clock), offered, fresh),
        )
        // Nothing picked and the ladder closed still opens on the one row that is free.
        assertEquals(listOf(NumbersExercise.Counting), DrillSelection.normalized(emptyList(), offered, fresh))
        // A Sprosse the run just booked lets both stand.
        assertEquals(
            listOf(NumbersExercise.Counting, NumbersExercise.Clock),
            DrillSelection.normalized(
                listOf(NumbersExercise.Clock, NumbersExercise.Counting),
                offered,
                mapOf(NumbersExercise.Counting to 7),
            ),
        )
    }

    @Test
    fun mixFlipsPerTaskWhileReverseHoldsOneDirection() {
        val rng = Random(21)
        val plain = NumbersMode(NumbersExercise.Counting, "de")
        val reverse = NumbersMode(listOf(NumbersExercise.Counting), "de", setOf(DrillModifier.Reverse))
        val mix = NumbersMode(listOf(NumbersExercise.Counting), "de", setOf(DrillModifier.Mix))
        val sprossen = mapOf(NumbersExercise.Counting to 3)

        fun NumbersMode.reversedDraw() = assertNotNull(draw(sprossen, null, emptySet(), rng).drawn).reversed
        assertTrue((1..20).none { plain.reversedDraw() })
        assertTrue((1..20).all { reverse.reversedDraw() })
        val flips = (1..40).map { mix.reversedDraw() }
        assertTrue(flips.contains(true) && flips.contains(false), "Mix flips per task: $flips")
    }

    /** Mix widens Forms out of the Numbers Sprosse — which means nothing without a Numbers Sprosse. */
    @Test
    fun mixWidensFormsOnlyWhileTheRunIsClimbingNumbers() {
        val both = NumbersMode(
            listOf(NumbersExercise.Counting, NumbersExercise.Forms), "de", setOf(DrillModifier.Mix),
        )
        assertTrue(both.mixesForms)
        assertFalse(NumbersMode(listOf(NumbersExercise.Forms), "de", setOf(DrillModifier.Mix)).mixesForms)
        val unmixed = NumbersMode(listOf(NumbersExercise.Counting, NumbersExercise.Forms), "de", emptySet())
        assertFalse(unmixed.mixesForms)
    }

    @Test
    fun fastHalvesTheSprosse() {
        val fast = NumbersMode(listOf(NumbersExercise.Counting), "de", setOf(DrillModifier.Fast))
        assertTrue(fast.winsToAdvance < NumbersMode(NumbersExercise.Counting, "de").winsToAdvance)
    }
}
