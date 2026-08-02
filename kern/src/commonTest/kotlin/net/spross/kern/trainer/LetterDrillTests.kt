package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.trainer.LetterDrill.AlphabetExampleWord

/** The ladder, the ramp, what may be asked at all, and how a typed glyph grades. */
class LetterDrillTests {
    private val fixture = LetterDrillFixture

    private fun sample(level: Int, seed: Int, refs: List<String> = fixture.allRefs, avoid: String? = null) =
        LetterDrill.sample(fixture.alphabet, fixture.example, level, refs, avoid, null, Random(seed))

    @Test
    fun theLadderMapsLevelsToStages() {
        assertEquals(LetterStage.ChoiceEasy, LetterDrill.stageFor(1))
        assertEquals(LetterStage.ChoiceEasy, LetterDrill.stageFor(2))
        for (level in 3..5) assertEquals(LetterStage.ChoiceConfusable, LetterDrill.stageFor(level))
        assertEquals(LetterStage.Typed, LetterDrill.stageFor(6))
        assertEquals(LetterStage.Typed, LetterDrill.stageFor(7))
        assertEquals(LetterStage.Dictation, LetterDrill.stageFor(8))
        assertEquals(LetterStage.Dictation, LetterDrill.stageFor(9))
        // Out of range coerces rather than throwing — a stale preset must not crash a run.
        assertEquals(LetterStage.ChoiceEasy, LetterDrill.stageFor(0))
        assertEquals(LetterStage.Dictation, LetterDrill.stageFor(99))
        assertEquals(9, LetterDrill.maxLevel(dictationAvailable = true))
        assertEquals(7, LetterDrill.maxLevel(dictationAvailable = false))
    }

    @Test
    fun entryLevelPacesOnTheWordsAlreadyHeld() {
        assertEquals(1, LetterDrill.entryLevel(0))
        assertEquals(1, LetterDrill.entryLevel(11))
        assertEquals(2, LetterDrill.entryLevel(12))
        assertEquals(5, LetterDrill.entryLevel(59))
        assertEquals(6, LetterDrill.entryLevel(60))
        assertEquals(6, LetterDrill.entryLevel(200))
        // Never dictation: entering on a stage that draws from the box would ask for a
        // word before the box can name five of them.
        assertTrue(LetterDrill.entryLevel(10_000) <= 6)
    }

    @Test
    fun stageLengthShrinksOnceAVocabularyIsHeld() {
        assertEquals(2, LetterDrill.winsToAdvance(0))
        assertEquals(2, LetterDrill.winsToAdvance(59))
        assertEquals(1, LetterDrill.winsToAdvance(60))
        assertEquals(1, LetterDrill.winsToAdvance(200))
    }

    @Test
    fun twoCleanWinsClimbOneRungAndAMissStepsBack() {
        val first = LetterDrill.advance(3, 0, correct = true, clean = true, maxLevel = 9, winsRequired = 2)
        assertEquals(LetterDrill.LetterDrillProgress(3, 1), first)
        val second = LetterDrill.advance(3, 1, correct = true, clean = true, maxLevel = 9, winsRequired = 2)
        assertEquals(LetterDrill.LetterDrillProgress(4, 0), second)
        val missed = LetterDrill.advance(4, 1, correct = false, clean = true, maxLevel = 9, winsRequired = 2)
        assertEquals(LetterDrill.LetterDrillProgress(3, 0), missed)
        // The floor holds however long the run goes wrong.
        assertEquals(
            LetterDrill.LetterDrillProgress(1, 0),
            LetterDrill.advance(1, 0, correct = false, clean = true, maxLevel = 9, winsRequired = 2),
        )
    }

    @Test
    fun aHeldVocabularyClimbsOnASingleWin() {
        assertEquals(
            LetterDrill.LetterDrillProgress(4, 0),
            LetterDrill.advance(3, 0, correct = true, clean = true, maxLevel = 9, winsRequired = 1),
        )
        // The narrower stage does not change what a miss costs.
        assertEquals(
            LetterDrill.LetterDrillProgress(2, 0),
            LetterDrill.advance(3, 0, correct = false, clean = true, maxLevel = 9, winsRequired = 1),
        )
    }

    @Test
    fun anAmberAnswerMovesNeitherWay() {
        for (width in 1..2) {
            assertEquals(
                LetterDrill.LetterDrillProgress(3, 1),
                LetterDrill.advance(3, 1, correct = true, clean = false, maxLevel = 9, winsRequired = width),
            )
        }
    }

    @Test
    fun theCeilingHolds() {
        val atTop = LetterDrill.advance(7, 1, correct = true, clean = true, maxLevel = 7, winsRequired = 2)
        assertEquals(7, atTop.level)
        val silent = LetterDrill.advance(9, 0, correct = true, clean = true, maxLevel = 9, winsRequired = 1)
        assertEquals(9, silent.level)
        // A level above the ceiling (dictation lost while the run was in it) drops into range.
        assertEquals(7, LetterDrill.advance(9, 0, correct = true, clean = true, maxLevel = 7, winsRequired = 1).level)
    }

    @Test
    fun onlyEntriesThatCanBeAskedAreEverPrompted() {
        val asked = (1..400).map { sample(level = 1, seed = it).answerRef }.toSet()
        // The prose rule row: never a question, never a tile.
        assertFalse("b d g" in asked)
        // Silent by authoring — it stays on the tiles and out of the prompts.
        assertFalse("h-length" in asked)
        // No example authored at all, so no gap word can be cut: filtered defensively.
        assertFalse("qu" in asked)
        assertTrue(asked.containsAll(setOf("m", "n", "u", "v", "f", "ß", "ss", "ch-ich", "ch-ach")))
    }

    @Test
    fun thePromptableListIsTheOuterBound() {
        val refs = listOf("m", "ß")
        val asked = (1..100).map { sample(level = 1, seed = it, refs = refs).answerRef }.toSet()
        assertEquals(setOf("m", "ß"), asked)
    }

    @Test
    fun thePromptCarriesItsProvenance() {
        val tasks = (1..400).map { sample(level = 6, seed = it) }.associateBy { it.answerRef }
        // A letter speaks its NAME, and the manifest key rides along for the recording.
        val letter = tasks.getValue("m")
        assertEquals("em", letter.promptText)
        assertEquals(LetterPromptKind.Name, letter.promptKind)
        assertEquals("m", letter.promptGlyph)
        assertNull(letter.promptSlug)
        assertNull(letter.gapText)
        assertNull(letter.gloss)
        // A resolved concept realization carries its slug — that slug's recording says it.
        val word = tasks.getValue("ß")
        assertEquals("Straße", word.promptText)
        assertEquals(LetterPromptKind.Word, word.promptKind)
        assertEquals("street", word.promptSlug)
        assertNull(word.promptGlyph)
        // BOTH fields authored, the slug unrealized here: the fallback text is PLAIN, never
        // the slug — no recording may play over a word it does not speak.
        val degraded = tasks.getValue("ch-ich")
        assertEquals("Licht", degraded.promptText)
        assertEquals(LetterPromptKind.PlainText, degraded.promptKind)
        assertNull(degraded.promptSlug)
        // An escape-hatch text is plain for the same reason.
        assertEquals(LetterPromptKind.PlainText, tasks.getValue("ch-ach").promptKind)
    }

    @Test
    fun theGapBlanksTheAskedGraphemeAndNothingElse() {
        val tasks = (1..400).map { sample(level = 6, seed = it) }.associateBy { it.answerRef }
        assertEquals("Stra＿e", tasks.getValue("ß").gapText)
        assertEquals("Wa＿er", tasks.getValue("ss").gapText)
        assertEquals("Na＿t", tasks.getValue("ch-ach").gapText)
        // The answer itself is the grapheme, and the word is kept back as the reveal gloss.
        assertEquals("ch", tasks.getValue("ch-ach").display)
        assertEquals("Nacht", tasks.getValue("ch-ach").gloss)
        assertEquals(listOf("ch"), tasks.getValue("ch-ach").accepted)
    }

    @Test
    fun theWordJustAskedIsResampledOnce() {
        val refs = listOf("m", "n")
        val repeats = (1..200).count { sample(level = 1, seed = it, refs = refs, avoid = "m").answerRef == "m" }
        // One resample on a hit: a repeat now needs two unlucky draws, ~¼ of them.
        assertTrue(repeats in 20..80, "repeats after one resample: $repeats of 200")
    }

    /**
     * A run of pooled draws on the one row that offers a choice — the `ß` row, handed four
     * words. One rng across the run, as a real sitting has it.
     */
    private fun gapped(
        words: List<AlphabetExampleWord>,
        count: Int = 200,
        avoidWord: String? = null,
    ): List<String> {
        val rng = Random(4)
        return (1..count).map {
            LetterDrill.sample(fixture.alphabet, { words }, 6, listOf("ß"), null, avoidWord, rng).promptText
        }
    }

    private val sharpWords =
        listOf("Straße", "Fuß", "groß", "heiß").map { AlphabetExampleWord(it, null) }

    @Test
    fun aRowWithSeveralWordsGapsADifferentOneEachTime() {
        assertEquals(
            sharpWords.map { it.text }.toSet(),
            gapped(sharpWords).toSet(),
            "every word the caller offered must be reachable",
        )
    }

    @Test
    fun theWordItJustGappedIsResampledOnce() {
        val repeats = gapped(sharpWords, avoidWord = "Fuß").count { it == "Fuß" }
        // Same courtesy as the entry draw: a repeat needs two unlucky draws out of four.
        assertTrue(repeats in 2..30, "repeats after one resample: $repeats of 200")
    }

    /**
     * Known words lead — but only while there are enough of them, or a beginner holding
     * three words would meet the same three all evening.
     */
    @Test
    fun knownWordsAreDrawnWhileEnoughOfThemExist() {
        val known = listOf("Straße", "Fuß", "groß").map { AlphabetExampleWord(it, null, known = true) }
        val stranger = AlphabetExampleWord("heiß", null)
        assertFalse("heiß" in gapped(known + stranger), "a stranger displaced a word the learner holds")
        assertTrue(
            "heiß" in gapped(known.take(2) + stranger),
            "below the floor the whole pool must open up",
        )
    }

    @Test
    fun aRowWhoseWordsCannotBeGappedIsNeverAsked() {
        // "Wasser" holds no ß at all — the pool empties and the row leaves the draw.
        val unusable = listOf(AlphabetExampleWord("Wasser", null))
        assertFailsWith<IllegalArgumentException> {
            LetterDrill.sample(fixture.alphabet, { unusable }, 6, listOf("ß"), null, null, Random(1))
        }
    }

    @Test
    fun typedGlyphsGradeExactlyAcrossCaseFormAndApostrophe() {
        val task = task(accepted = listOf("ü"))
        assertTrue(LetterDrill.gradeLetter("ü", task))
        assertTrue(LetterDrill.gradeLetter("Ü", task))
        assertTrue(LetterDrill.gradeLetter("  ü ", task))
        // Decomposed input off an international keyboard is the same letter.
        assertTrue(LetterDrill.gradeLetter("ü", task))
        assertFalse(LetterDrill.gradeLetter("u", task))
        assertFalse(LetterDrill.gradeLetter("", task))

        // The whole apostrophe class means the one letter the alphabet files store.
        val apostrophe = task(accepted = listOf("ʼ"))
        for (typed in listOf("'", "’", "ʼ")) {
            assertTrue(LetterDrill.gradeLetter(typed, apostrophe), "$typed must grade as the apostrophe")
        }

        // Multigraphs grade exactly too — no typo budget anywhere near a one-glyph answer.
        val multi = task(accepted = listOf("sch"))
        assertTrue(LetterDrill.gradeLetter("SCH", multi))
        assertFalse(LetterDrill.gradeLetter("sh", multi))
    }

    private fun task(accepted: List<String>) = LetterDrillTask(
        stage = LetterStage.Typed,
        language = LetterDrillFixture.LANGUAGE,
        answerRef = accepted.first(),
        promptText = "name",
        promptKind = LetterPromptKind.Name,
        promptSlug = null,
        promptGlyph = accepted.first(),
        choices = null,
        gapText = null,
        accepted = accepted,
        display = accepted.first(),
        gloss = null,
    )

    @Test
    fun anUnrealizedSlugNeverKeepsItsProvenance() {
        // The resolver decides: same entry, a realization present, and the task is a Word.
        val resolved: (AlphabetEntry) -> List<AlphabetExampleWord> = { entry ->
            listOfNotNull(
                entry.exampleSlug?.let { AlphabetExampleWord("Licht", it) }
                    ?: entry.exampleText?.let { AlphabetExampleWord(it, null) },
            )
        }
        val task = LetterDrill.sample(
            fixture.alphabet, resolved, 6, listOf("ch-ich"), null, null, Random(1),
        )
        assertEquals(LetterPromptKind.Word, task.promptKind)
        assertEquals("light", task.promptSlug)
    }
}
