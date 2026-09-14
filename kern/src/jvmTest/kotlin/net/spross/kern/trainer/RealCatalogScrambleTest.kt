package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Language

/**
 * The two scrambles against the SHIPPING catalog — rules only, never a pinned draw.
 *
 * A box where every card has grown is the widest pool either drill can ever see, so what the
 * eligibility gates let through here is exactly what they let through in the field. The
 * assertions the synthetic fixtures cannot make are the ones that need real content: that each
 * gate still EXCLUDES something (an authored `…` frame, a two-word verb, a three-letter word),
 * which is what would quietly stop being true if a rule were dropped.
 */
class RealCatalogScrambleTest {

    private val pairs = listOf("en" to "de", "de" to "sw", "de" to "fr")

    private fun cards(source: Language, target: Language): List<Card> =
        RealCatalog.catalog.join(source, target)

    /** Every card grown past the display bar: phrase unlock opens and the word pool is widest. */
    private fun grown(cards: List<Card>): BoxState {
        var state = Box.state(cards)
        for (card in cards) {
            state = Box.inject(
                state,
                Box.sched(
                    card.id,
                    stability = 60.0,
                    dueMillis = Box.plusDays(Box.day1, 3.0),
                    lastReviewMillis = Box.day1,
                ),
            )
        }
        return state
    }

    // MARK: - Word scramble

    /** Every form a run could hand over is one word, written in letters, long enough to mix. */
    @Test
    fun everyFormOfferedIsOneSpellingLongEnoughToAnchor() {
        for ((source, target) in pairs) {
            val words = WordScrambleAvailability.report(grown(cards(source, target))).words
            assertTrue(words.size >= WordScrambleAvailability.POOL_FLOOR, "$target: ${words.size} words")
            for (spelling in words) {
                val card = spelling.card
                assertTrue(card.kind != CardKind.Phrase && card.kind != CardKind.Idiom, "$target: ${card.id}")
                for (form in spelling.forms) {
                    assertEquals(1, ScrambleTokenizer.tokens(form).size, "$target: \"$form\" is not one token")
                    assertTrue(spellableOutOfLetters(form), "$target: \"$form\" is not spelled in letters")
                    assertTrue(
                        form.count { it.isLetter() } >= WordScrambleAvailability.MIN_LETTERS,
                        "$target: \"$form\" is too short to anchor",
                    )
                }
            }
        }
    }

    /**
     * The gate bites on real content: the catalog authors multi-token words (reflexive and
     * separable verbs), words below the letter floor, and forms no letters can spell — a
     * Swahili bound stem ("-baya"), a hyphenated compound ("T-Shirt"). None reaches the pool
     * as it stands; a stem is asked through the concrete forms it agrees into instead.
     */
    @Test
    fun theWordGateStillExcludesRealContent() {
        val cards = cards("en", "de")
        val offered = WordScrambleAvailability.report(grown(cards)).words.map { it.card.id }.toSet()
        val singleWordKinds = cards.filter { it.kind != CardKind.Phrase && it.kind != CardKind.Idiom }
        val multiToken = singleWordKinds.filter { ScrambleTokenizer.tokens(it.target.text).size > 1 }
        val tooShort = singleWordKinds.filter {
            it.target.text.count { ch -> ch.isLetter() } < WordScrambleAvailability.MIN_LETTERS
        }
        val hyphenated = singleWordKinds.filter { '-' in it.target.text }
        assertTrue(multiToken.isNotEmpty(), "de authors no multi-token word — the filter is untested")
        assertTrue(tooShort.isNotEmpty(), "de authors no short word — the filter is untested")
        assertTrue(hyphenated.isNotEmpty(), "de authors no hyphenated word — the filter is untested")
        assertTrue(multiToken.none { it.id in offered })
        assertTrue(tooShort.none { it.id in offered })
        assertTrue(hyphenated.none { it.id in offered })

        // Swahili's bound stems: never asked as authored, always through an agreeing form.
        val stems = cards("de", "sw").filter { it.target.text.startsWith("-") }
        assertTrue(stems.size >= 20, "sw authors ${stems.size} bound stems — the fallback is untested")
        val sw = WordScrambleAvailability.report(grown(cards("de", "sw")))
        val asked = sw.words.filter { it.card.target.text.startsWith("-") }
        assertTrue(asked.isNotEmpty(), "no bound stem reached the pool through its variants")
        assertTrue(asked.all { spelling -> spelling.forms.none { it.startsWith("-") } })
    }

    /** Every question a sweep draws mixes the word's own letters, and never into the word itself. */
    @Test
    fun everyWordQuestionMixesTheSpellingItAsksFor() {
        for ((source, target) in pairs + ("de" to "uk")) {
            val config = WordScrambleRunConfig(
                report = WordScrambleAvailability.report(grown(cards(source, target))),
                normalizer = null,
            )
            for (level in 1..WordScrambleMasking.MAX_LEVEL) {
                var state = WordScrambleRun.openAt(config, level, Random(level * 31 + target.hashCode()))
                repeat(40) {
                    val task = state.task ?: return@repeat
                    val display = task.scrambled.display
                    assertTrue(
                        spellableOutOfLetters(display),
                        "$target: \"$display\" mixes something that is not a letter",
                    )
                    assertEquals(
                        task.display.lowercase().toList().sorted(),
                        display.lowercase().toList().sorted(),
                        "$target: \"$display\" is not the letters of \"${task.display}\"",
                    )
                    assertFalse(
                        display.lowercase() == task.display.lowercase() && distinctLetters(task.display) > 1,
                        "$target: \"${task.display}\" was handed back unmixed",
                    )
                    assertEquals(listOf(task.display), task.accepted)
                    assertTrue(task.gloss.isNotBlank(), "$target: ${task.cardId} has nothing to reveal")
                    state = WordScrambleRun.reduce(
                        WordScrambleRun.reduce(state, WordScrambleIntent.Submit(task.display), Random(1)).state,
                        WordScrambleIntent.ConfirmPending,
                        Random(2),
                    ).state
                }
            }
        }
    }

    // MARK: - Sentence scramble

    @Test
    fun everyPhraseOfferedHasAWordOrderToPutBack() {
        for ((source, target) in pairs) {
            val report = SentenceScrambleAvailability.report(grown(cards(source, target)))
            assertTrue(
                report.phrases.size >= SentenceScrambleAvailability.POOL_FLOOR,
                "$target: ${report.phrases.size} phrases",
            )
            for (phrase in report.phrases) {
                val text = phrase.card.target.text
                assertEquals(CardKind.Phrase, phrase.card.kind, "$target: ${phrase.card.id}")
                assertTrue(
                    phrase.atoms.size >= SentenceScrambleAvailability.MIN_ATOMS,
                    "$target: \"$text\" has no order to put back",
                )
                assertFalse('…' in text, "$target: \"$text\" is an authored blank")
                // The round trip is what lets the platforms render atoms instead of the phrase.
                assertEquals(text, ScrambleTokenizer.joined(phrase.atoms), "$target: \"$text\" does not rejoin")
            }
        }
    }

    /**
     * The gate bites on real content: the catalog authors `…` frames and phrases of one or two
     * words, and neither reaches the pool.
     */
    @Test
    fun theSentenceGateStillExcludesRealContent() {
        val cards = cards("en", "de")
        val offered = SentenceScrambleAvailability.report(grown(cards)).phrases.map { it.card.id }.toSet()
        val phrases = cards.filter { it.kind == CardKind.Phrase }
        val blanks = phrases.filter { '…' in it.target.text }
        val short = phrases.filter {
            ScrambleTokenizer.tokens(it.target.text).size < SentenceScrambleAvailability.MIN_ATOMS
        }
        assertTrue(blanks.isNotEmpty(), "de authors no … frame — the filter is untested")
        assertTrue(short.isNotEmpty(), "de authors no short phrase — the filter is untested")
        assertTrue(blanks.none { it.id in offered })
        assertTrue(short.none { it.id in offered })
    }

    /** Every question a sweep draws deals the phrase's own atoms, and never in its own order. */
    @Test
    fun everyArrangementIsDealtOutOfOrder() {
        for ((source, target) in pairs) {
            val config = SentenceScrambleRunConfig(
                SentenceScrambleAvailability.report(grown(cards(source, target))),
            )
            var state = SentenceScrambleRun.openAt(config, 1, Random(target.hashCode()))
            repeat(60) {
                val task = state.task ?: return@repeat
                assertEquals(
                    task.canonical.map { it.id }.sorted(),
                    task.shuffled.map { it.id }.sorted(),
                    "$target: ${task.cardId} was dealt atoms it does not own",
                )
                assertFalse(
                    ScrambleGrading.isSolved(task.shuffled, task.canonical),
                    "$target: ${task.cardId} was dealt in its own order",
                )
                assertEquals(task.display, ScrambleTokenizer.joined(task.canonical))
                assertTrue(task.gloss.isNotBlank(), "$target: ${task.cardId} has nothing to reveal")
                state = solve(state)
            }
        }
    }

    /** Arrange the question on screen correctly and book it. */
    private fun solve(state: SentenceScrambleRunState): SentenceScrambleRunState {
        val task = assertNotNull(state.task)
        val arranged = task.canonical.fold(state) { carried, atom ->
            SentenceScrambleRun.reduce(
                carried,
                SentenceScrambleIntent.PlaceAtom(task.shuffled.indexOfFirst { it.id == atom.id }),
                Random(3),
            ).state
        }
        return SentenceScrambleRun.reduce(
            arranged,
            SentenceScrambleIntent.ConfirmPending,
            Random(4),
        ).state
    }

    private fun distinctLetters(text: String): Int = text.lowercase().toSet().size

    /**
     * Letters and nothing else — plus the apostrophe, which sw "ng'ombe" and uk "м'який" are
     * SPELLED with. A hyphen, a digit or a stop is not something a scramble may hand over.
     */
    private fun spellableOutOfLetters(form: String): Boolean =
        form.all { it.isLetter() || it == '\'' || it == '’' || it == 'ʼ' }
}
