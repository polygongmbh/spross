package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Language
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.Match

/**
 * What a dictated word is graded against, on the real catalog.
 *
 * Two things have to hold at once, and they pull in opposite directions. A word that is
 * really ANOTHER concept's answer must never be forgiven as a slip — sw `kufunga` sits one
 * edit from `kufungua`, and the drill's strict normalizer hands out one typo per word, so
 * the bare normalizer would accept the opposite verb. And the learner's OWN word must
 * never come back named as somebody else's, which is what would happen the moment the
 * grading card carried a synthetic id instead of the real one.
 */
class LetterDictationGradingTest {
    private val catalog get() = RealCatalog.catalog

    /**
     * The drill's normalizer: articles graded as typed, one slip per word
     * (`kern/docs/grading.md`).
     */
    private fun normalizer(target: Language) =
        AnswerNormalizer(catalog.languages.getValue(target), articleLeniency = false, maxTyposPerWord = 1)

    private fun grading(card: Card): Card = LetterDrill.dictationGradingCard(
        card,
        assertNotNull(
            LetterDrill.sampleDictation(
                listOf(LetterDrill.DictationCandidate(card)), null, 9, null, emptySet(), Random(1),
            ),
        ),
    )

    private fun List<Card>.byText(text: String): Card =
        firstOrNull { it.target.text == text } ?: throw AssertionError("no card answers \"$text\"")

    @Test
    fun theNeighboringVerbIsNamedAsItselfNotForgivenAsASlip() {
        val cards = catalog.join("de", "sw")
        val spoken = grading(cards.byText("kufungua"))
        // One card at a time the missing "u" fits the drill's per-word budget — the bug.
        assertIs<Match.Typo>(normalizer("sw").evaluate("kufunga", spoken))

        val verdict = CatalogAnswerGrader(normalizer("sw"), cards).grade("kufunga", spoken)
        assertIs<Match.OtherWord>(verdict, "the catalog owns \"kufunga\" — typing it is that word")
        assertEquals("kufunga", verdict.word)
    }

    @Test
    fun theUkrainianNumberPairIsNotBridgedEither() {
        // The pair the typo-bridge guard names for uk (`kern/docs/grading.md`): it lives in
        // the number pack rather than the catalog, so it can never be DICTATED — but it is
        // the language's known one-edit collision, and the grading path is the same one.
        val nine = Trainer.number(9, "uk").display
        val ten = Trainer.number(10, "uk").display
        val cards = listOf(numberCard("nine", nine), numberCard("ten", ten))
        val spoken = grading(cards.byText(ten))
        assertIs<Match.Typo>(normalizer("uk").evaluate(nine, spoken), "$nine/$ten stopped colliding")

        val verdict = CatalogAnswerGrader(normalizer("uk"), cards).grade(nine, spoken)
        assertIs<Match.OtherWord>(verdict)
        assertEquals(nine, verdict.word)
    }

    @Test
    fun theSpokenWordsOwnVariantIsNeverSomebodyElsesWord() {
        val cards = catalog.join("de", "uk")
        val mouse = cards.byText("миша")
        val variant = "мишка"
        // The app's almost step reads exactly this set.
        assertTrue(
            variant in mouse.target.synonyms + mouse.target.variants,
            "the catalog no longer teaches \"$variant\" as a form of \"${mouse.target.text}\"",
        )

        val verdict = CatalogAnswerGrader(normalizer("uk"), cards).grade(variant, grading(mouse))
        assertFalse(
            verdict is Match.OtherWord,
            "the learner's own concept came back as another word: $verdict",
        )

        // The proof that the real id is what holds that: a synthetic one breaks it, and the
        // drill would answer "мишка" with "that is somebody's word for mouse — миша".
        val impostor = grading(mouse).copy(id = "drill", feminineOf = null)
        val leak = CatalogAnswerGrader(normalizer("uk"), cards).grade(variant, impostor)
        assertIs<Match.OtherWord>(leak, "the guard has stopped guarding anything")
        assertEquals(mouse.target.text, leak.word)
    }

    private fun numberCard(id: String, reading: String): Card = Card(
        id = id,
        kind = CardKind.Noun,
        area = "numbers",
        emoji = null,
        seedIndex = 0,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "de", text = id),
        target = Realization(lang = "uk", text = reading),
        promptFeminineMarker = false,
    )
}
