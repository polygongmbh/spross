package net.spross.kern.trainer

import net.spross.kern.box.Box
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.session.AnswerNormalizer

/**
 * A hand-built German box for the two scramble drills — words and phrases of every shape their
 * eligibility gates have an opinion about, so neither test has to reach for shipped content.
 */
internal object ScrambleFixture {
    const val TARGET = "de"

    /** Past the display bar the word scramble reads, and past the growing bar phrase unlock does. */
    const val CONSOLIDATED = 30.0

    /** Growing, but short of the display bar — a word this drill may not ask. */
    const val GROWING = 10.0

    val language: LanguageInfo = Fixture.catalog().languages.getValue(TARGET)

    val normalizer: AnswerNormalizer = AnswerNormalizer.drill(language)

    fun word(
        id: String,
        text: String,
        kind: CardKind = CardKind.Noun,
        seed: Int = 0,
        synonyms: List<String> = emptyList(),
        variants: List<String> = emptyList(),
    ): Card = Card(
        id = id,
        kind = kind,
        area = "fixture",
        emoji = null,
        seedIndex = seed,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "en", text = "en-$id"),
        target = Realization(lang = TARGET, text = text, synonyms = synonyms, variants = variants),
        promptFeminineMarker = false,
    )

    fun phrase(
        id: String,
        text: String,
        components: List<String>,
        seed: Int = 50,
    ): Card = Card(
        id = id,
        kind = CardKind.Phrase,
        area = "fixture",
        emoji = null,
        seedIndex = seed,
        components = components,
        feminineOf = null,
        source = Realization(lang = "en", text = "en-$id"),
        target = Realization(lang = TARGET, text = text),
        promptFeminineMarker = false,
    )

    /**
     * A box where every card carries [stability] unless [standing] names another figure for it.
     * A card listed in [suspended] keeps its schedule and sleeps.
     */
    fun box(
        cards: List<Card>,
        stability: Double = CONSOLIDATED,
        standing: Map<String, Double> = emptyMap(),
        suspended: Set<String> = emptySet(),
    ): BoxState {
        var state = Box.state(cards)
        for (card in cards) {
            state = Box.inject(
                state,
                Box.sched(
                    card.id,
                    stability = standing[card.id] ?: stability,
                    dueMillis = Box.plusDays(Box.day1, 3.0),
                    lastReviewMillis = Box.day1,
                    suspended = card.id in suspended,
                ),
            )
        }
        return state
    }
}
