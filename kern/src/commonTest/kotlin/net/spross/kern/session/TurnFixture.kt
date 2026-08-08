package net.spross.kern.session

import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.Realization

/**
 * Cards and one machine both turn suites answer against: sw answers, a near-twin pair the
 * catalog owns on both sides, and a card with a synonym so the ear rule has something to
 * forgive.
 */
internal object TurnFixture {
    const val T0 = 1_700_000_000_000L

    private val catalog = Fixture.catalog()
    val normalizer = AnswerNormalizer(catalog.languages.getValue("sw"))

    fun card(
        id: String,
        source: String,
        target: String,
        kind: CardKind,
        seedIndex: Int = 0,
        synonyms: List<String> = emptyList(),
        variants: List<String> = emptyList(),
    ): Card = Card(
        id = id, kind = kind, area = "test", emoji = null, seedIndex = seedIndex,
        components = emptyList(), feminineOf = null,
        source = Realization(lang = "de", text = source),
        target = Realization(lang = "sw", text = target, synonyms = synonyms, variants = variants),
        promptFeminineMarker = false,
    )

    /** Four letters: exactly at the typo budget's floor, so one slip is forgiven. */
    val knife = card("knife", "Messer", "kisu", CardKind.Noun, seedIndex = 0)
    val open = card("open", "öffnen", "kufungua", CardKind.Verb, seedIndex = 1)

    /** One edit from [open] and owned by the catalog — a word, never a slip of it. */
    val close = card("close", "schließen", "kufunga", CardKind.Verb, seedIndex = 2)
    val language = card("language", "Sprache", "lugha", CardKind.Noun, seedIndex = 3)

    /** The variant repeats the spoken form: a card that also lists the word it plays. */
    val car = card(
        "car", "Auto", "gari", CardKind.Noun, seedIndex = 4,
        synonyms = listOf("motokaa"), variants = listOf("Gari"),
    )

    private val machine = TurnMachine(
        CatalogAnswerGrader(normalizer, listOf(knife, open, close, language, car)),
        normalizer,
    )

    fun produce(
        card: Card,
        prompt: ProducePrompt = ProducePrompt.Source,
        firstExposure: Boolean = false,
        consolidated: Boolean = false,
    ): TurnState = machine.begin(
        card, PresentationRole.Produce, prompt,
        if (prompt == ProducePrompt.Sound) card.target.text else card.source.text,
        firstExposure, consolidated, T0,
    )

    fun recognize(
        card: Card,
        firstExposure: Boolean = false,
        consolidated: Boolean = false,
    ): TurnState = machine.begin(
        card, PresentationRole.Recognize, ProducePrompt.Source, card.target.text,
        firstExposure, consolidated, T0,
    )

    fun step(state: TurnState, intent: TurnIntent, nowMillis: Long = T0): TurnReduction =
        machine.reduce(state, intent, nowMillis)

    /** [step] where only the next state matters. */
    fun state(state: TurnState, intent: TurnIntent, nowMillis: Long = T0): TurnState =
        step(state, intent, nowMillis).state
}
