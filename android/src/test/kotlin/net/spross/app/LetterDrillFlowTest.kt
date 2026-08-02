package net.spross.app

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.trainer.LetterDrill
import net.spross.kern.trainer.LetterStage

/**
 * The run, not the rules: kern owns the ladder, the draw and the ramp step and tests them
 * itself. What is asserted here is what the app layer adds — that a question ends by
 * silencing whatever is sounding, that an amber answer moves the rung neither way, and
 * that a dictated synonym is the amber verdict rather than a miss.
 */
class LetterDrillFlowTest {

    private fun letter(glyph: String, name: String) = AlphabetEntry(
        ref = glyph,
        glyph = glyph,
        upper = glyph.uppercase(),
        kind = AlphabetKind.Letter,
        name = name,
        ipa = glyph,
        exampleSlug = null,
        exampleText = null,
        hints = emptyMap(),
        context = emptyMap(),
        drill = true,
        mine = true,
        section = null,
        confusableLook = emptyList(),
        confusableSound = emptyList(),
    )

    private val alphabet = Alphabet(
        language = "uk",
        sections = emptyList(),
        entries = listOf(
            letter("а", "а"), letter("б", "бе"), letter("в", "ве"),
            letter("г", "ге"), letter("д", "де"), letter("е", "е"),
        ),
    )

    private fun card(id: String, text: String, synonyms: List<String> = emptyList()) = Card(
        id = id,
        kind = CardKind.Noun,
        area = "test",
        emoji = null,
        seedIndex = 0,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "de", text = "Wort $id"),
        target = Realization(lang = "uk", text = text, synonyms = synonyms),
        promptFeminineMarker = false,
    )

    private val dictationCards = listOf(
        card("mouse", "миша", synonyms = listOf("мишка")),
        card("bread", "хліб"),
        card("water", "вода"),
        card("house", "дім"),
        card("city", "місто"),
    )

    private val language = LanguageInfo(code = "uk", name = "Українська", englishName = "Ukrainian", flag = "🇺🇦")

    private var silenced = 0

    private fun flow(settled: Int = 0, dictation: Boolean = false): LetterDrillFlow {
        val cards = if (dictation) dictationCards else emptyList()
        return LetterDrillFlow(
            availability = LetterDrillAvailability(
                language = "uk",
                alphabet = alphabet,
                promptableRefs = alphabet.entries.map { it.ref },
                dictationCandidates = cards.map { LetterDrill.DictationCandidate(it) },
            ),
            settledCards = settled,
            cards = cards.associateBy { it.id },
            dictationGrader = CatalogAnswerGrader(
                AnswerNormalizer(language, articleLeniency = false, maxTyposPerWord = 1),
                cards,
            ),
            silence = { silenced += 1 },
            rng = Random(42),
        )
    }

    /** The correct tile, or the answer typed — whichever this rung asks for. */
    private fun answerCorrectly(flow: LetterDrillFlow) {
        val task = flow.task ?: error("nothing to answer")
        if (task.stage == LetterStage.Typed || task.stage == LetterStage.Dictation) {
            flow.input = task.display
            flow.submit()
        } else {
            flow.choose(task.display)
        }
        flow.next()
    }

    @Test
    fun aRunOpensOnAQuestionItCanAsk() {
        val drill = flow()
        val task = assertNotNull(drill.task)
        assertEquals(LetterStage.ChoiceEasy, task.stage)
        assertEquals(1, drill.level)
        assertTrue(task.promptText.isNotEmpty(), "a question is always a spoken form")
        assertTrue(task.choices.orEmpty().contains(task.display))
    }

    // D5: a clip must never follow the learner onto the next question — so every path
    // that ends one cuts playback first, and none of them may be forgotten.
    @Test
    fun everyPathThatEndsAQuestionSilencesFirst() {
        silenced = 0
        val drill = flow()
        drill.choose(drill.task!!.display)
        assertEquals(1, silenced)
        drill.next() // advance
        assertEquals(2, silenced)

        val typed = flow(settled = 72)
        silenced = 0
        typed.reveal()
        assertEquals(1, silenced)
        typed.next()
        assertEquals(2, silenced)
        typed.input = typed.task!!.display
        typed.submit()
        assertEquals(3, silenced)
        typed.close()
        assertEquals(5, silenced) // close() silences, then books through next()
    }

    @Test
    fun twoCleanWinsClimbARungAndAMissStepsBackDown() {
        val drill = flow() // 0 settled ⇒ the classic two wins per rung
        answerCorrectly(drill)
        assertEquals(1, drill.level)
        answerCorrectly(drill)
        assertEquals(2, drill.level)

        val task = assertNotNull(drill.task)
        drill.choose(task.choices!!.first { it != task.display })
        drill.next()
        assertEquals(1, drill.level)
        assertEquals(0, drill.streak)
    }

    // A revealed answer is a miss, whatever stands in the field afterwards.
    @Test
    fun revealingFillsTheFieldAndBooksAMiss() {
        val drill = flow(settled = 72) // enters at Typed
        assertEquals(LetterStage.Typed, drill.task!!.stage)
        assertEquals(6, drill.level)
        drill.reveal()
        assertEquals(drill.task!!.display, drill.input)
        drill.next()
        assertEquals(5, drill.level)
    }

    @Test
    fun oneCleanWinIsEnoughOnceAVocabularyHasSettled() {
        val drill = flow(settled = 72)
        answerCorrectly(drill)
        assertEquals(7, drill.level)
    }

    /**
     * The dictation verdict order: a form the card itself teaches ("auch: …") is amber
     * and names what actually played — never wrong, and never reported back to the
     * learner as somebody else's word.
     */
    @Test
    fun aDictatedSynonymIsAmberAndNamesTheFormThatPlayed() {
        val drill = flow(settled = 72, dictation = true)
        // Climb to dictation, then keep drawing until the word with a taught variant
        // comes up — the draw is kern's, and this asserts the verdict, not the seed.
        repeat(40) { if (drill.task?.display != "миша") answerCorrectly(drill) }
        val task = assertNotNull(drill.task)
        assertEquals(LetterStage.Dictation, task.stage)
        assertEquals("миша", task.display)

        val level = drill.level
        drill.input = "мишка"
        drill.submit()
        val feedback = drill.feedback
        assertTrue(feedback is LetterFeedback.Correct, "a taught form is never a miss")
        assertEquals(LetterCorrection.Kind.Heard, feedback.correction?.kind)
        assertEquals("миша", feedback.correction?.form)
        drill.next()
        // Amber: neither banked nor punished.
        assertEquals(level, drill.level)
    }

    @Test
    fun closingAnUntouchedRunHasNothingToSummarise() {
        val drill = flow()
        assertFalse(drill.close())
        assertFalse(drill.finished)

        val answered = flow()
        answerCorrectly(answered)
        assertTrue(answered.close())
        assertTrue(answered.finished)
        assertEquals(1, answered.done)
    }
}
