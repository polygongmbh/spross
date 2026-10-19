package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.trainer.LetterDrill.AlphabetExampleWord

/**
 * The ladder, what may be asked at all, and how a typed glyph grades.
 * The Sprosse ramp itself is shared with the slot drill and lives in [DrillProgressionTests].
 */
class LetterDrillTests {
    private val fixture = LetterDrillFixture

    private fun sample(sprosse: Int, seed: Int, refs: List<String> = fixture.allRefs, avoid: String? = null) =
        assertNotNull(
            LetterDrill.sample(
                fixture.alphabet, fixture.example, sprosse, refs, avoid, null, emptySet(), Random(seed),
            ),
        )

    /** Formats only ever climb, dictation tops the ladder, and a stale preset coerces rather than crashes. */
    @Test
    fun theLadderClimbsThroughTheFormatsInOrder() {
        val withDictation = LetterDrill.maxSprosse(dictationAvailable = true)
        val formats = (1..withDictation).map { LetterDrill.formatFor(it) }
        assertEquals(formats.sorted(), formats)
        assertEquals(LetterFormat.entries.toList(), formats.distinct())
        assertEquals(LetterFormat.Typed, LetterDrill.formatFor(LetterDrill.maxSprosse(dictationAvailable = false)))
        assertEquals(LetterFormat.ChoiceEasy, LetterDrill.formatFor(0))
        assertEquals(LetterFormat.Dictation, LetterDrill.formatFor(99))
    }

    @Test
    fun onlyEntriesThatCanBeAskedAreEverPrompted() {
        val asked = (1..400).map { sample(sprosse = 1, seed = it).answerRef }.toSet()
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
        val asked = (1..100).map { sample(sprosse = 1, seed = it, refs = refs).answerRef }.toSet()
        assertEquals(setOf("m", "ß"), asked)
    }

    @Test
    fun thePromptCarriesItsProvenance() {
        val tasks = (1..400).map { sample(sprosse = 6, seed = it) }.associateBy { it.answerRef }
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
        val tasks = (1..400).map { sample(sprosse = 6, seed = it) }.associateBy { it.answerRef }
        assertEquals("Stra＿e", tasks.getValue("ß").gapText)
        assertEquals("Wa＿er", tasks.getValue("ss").gapText)
        assertEquals("Na＿t", tasks.getValue("ch-ach").gapText)
        // The answer itself is the grapheme, and the word is kept back as the reveal gloss.
        assertEquals("ch", tasks.getValue("ch-ach").display)
        assertEquals("Nacht", tasks.getValue("ch-ach").gloss)
        assertEquals(listOf("ch"), tasks.getValue("ch-ach").accepted)
    }

    /** The draw avoids the answer just asked ([DrillLadder.pickAvoiding]). */
    @Test
    fun theWordJustAskedIsResampledOnce() {
        fun repeats(avoid: String?) =
            (1..200).count { sample(sprosse = 1, seed = it, refs = listOf("m", "n"), avoid = avoid).answerRef == "m" }
        assertTrue(repeats("m") < repeats(null), "avoiding the answer just asked bought nothing")
    }

    /**
     * A run of pooled draws on the one row that offers a choice — the `ß` row, handed four
     * words. One rng across the run, as a real sitting has it.
     */
    private fun gapped(
        words: List<AlphabetExampleWord>,
        count: Int = 200,
    ): List<String> {
        val rng = Random(4)
        return (1..count).map {
            assertNotNull(
                LetterDrill.sample(
                    fixture.alphabet, { words }, 6, listOf("ß"), null, null, emptySet(), rng,
                ),
            ).promptText
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

    /**
     * Known words lead — but only while there are enough of them, or a beginner holding
     * three words would meet the same three all evening.
     */
    @Test
    fun arrivedWordsAreDrawnWhileEnoughOfThemExist() {
        val arrived = listOf("Straße", "Fuß", "groß").map { AlphabetExampleWord(it, null, arrived = true) }
        val stranger = AlphabetExampleWord("heiß", null)
        assertFalse("heiß" in gapped(arrived + stranger), "a stranger displaced a word the learner holds")
        assertTrue(
            "heiß" in gapped(arrived.take(2) + stranger),
            "below the floor the whole pool must open up",
        )
    }

    @Test
    fun aRowWhoseWordsCannotBeGappedIsNeverAsked() {
        // "Wasser" holds no ß at all — the pool empties and the row leaves the draw, which
        // leaves the Sprosse with nothing to ask rather than an unanswerable question.
        val unusable = listOf(AlphabetExampleWord("Wasser", null))
        assertNull(
            LetterDrill.sample(
                fixture.alphabet, { unusable }, 6, listOf("ß"), null, null, emptySet(), Random(1),
            ),
        )
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
        format = LetterFormat.Typed,
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
        val task = assertNotNull(
            LetterDrill.sample(
                fixture.alphabet, resolved, 6, listOf("ch-ich"), null, null, emptySet(), Random(1),
            ),
        )
        assertEquals(LetterPromptKind.Word, task.promptKind)
        assertEquals("light", task.promptSlug)
    }
}
