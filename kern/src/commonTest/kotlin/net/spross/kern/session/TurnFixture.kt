package net.spross.kern.session

import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.TurnAnswer
import net.spross.kern.model.PromptForm
import net.spross.kern.model.Realization
import net.spross.kern.model.turnPrompt

/**
 * Cards and one machine both turn suites answer against: sw answers, a near-twin pair the
 * catalog owns on both sides, and a card with a synonym so the ear rule has something to
 * forgive.
 */
internal object TurnFixture {
    const val T0 = 1_700_000_000_000L

    private val catalog = Fixture.catalog()
    val normalizer = AnswerNormalizer(catalog.languages.getValue("sw"))

    /** The SOURCE language's normalizer — what a card asked by ear grades its meaning with. */
    val meaningNormalizer = AnswerNormalizer(catalog.languages.getValue("de"))

    fun card(
        id: String,
        source: String,
        target: String,
        kind: CardKind,
        seedIndex: Int = 0,
        teaches: List<String> = emptyList(),
        accepts: List<String> = emptyList(),
        sourceTeaches: List<String> = emptyList(),
    ): Card = Card(
        id = id, kind = kind, area = "test", emoji = null, seedIndex = seedIndex,
        components = emptyList(),
        source = Realization(lang = "de", text = source, teaches = sourceTeaches),
        target = Realization(lang = "sw", text = target, teaches = teaches, accepts = accepts),
    )

    /** Four letters: exactly at the typo budget's floor, so one slip is forgiven. */
    val knife = card("knife", "Messer", "kisu", CardKind.Noun, seedIndex = 0)
    val open = card("open", "öffnen", "kufungua", CardKind.Verb, seedIndex = 1)

    /** One edit from [open] and owned by the catalog — a word, never a slip of it. */
    val close = card("close", "schließen", "kufunga", CardKind.Verb, seedIndex = 2)
    val language = card("language", "Sprache", "lugha", CardKind.Noun, seedIndex = 3)

    /**
     * The variant repeats the spoken form: a card that also lists the word it plays.
     * Its SOURCE side carries a synonym too, which is what a card asked by ear grades against.
     */
    val car = card(
        "car", "Auto", "gari", CardKind.Noun, seedIndex = 4,
        teaches = listOf("motokaa"), accepts = listOf("Gari"),
        sourceTeaches = listOf("Wagen"),
    )

    /** The merge the catalog documents: one Swahili verb printed by two German concepts. */
    val leave = card("leave", "verlassen", "kuacha", CardKind.Verb, seedIndex = 5)
    val quit = card("quit", "aufhören", "kuacha", CardKind.Verb, seedIndex = 6)

    /** The mirror merge: one German prompt shown by two concepts with a Swahili word each. */
    val go = card("go", "gehen", "kwenda", CardKind.Verb, seedIndex = 7)
    val walk = card("walk", "gehen", "kutembea", CardKind.Verb, seedIndex = 8)

    /** One family in two word classes: the noun's source shows the verb's word, article aside. */
    val cough = card("cough", "Husten", "kikohozi", CardKind.Noun, seedIndex = 9)
        .let { it.copy(source = it.source.copy(grammar = mapOf("article" to "der"))) }
    val toCough = card("to-cough", "husten", "kukohoa", CardKind.Verb, seedIndex = 10)

    private val machine = TurnMachine(
        CatalogAnswerGrader(normalizer, listOf(knife, open, close, language, car, leave, quit, go, walk, cough, toCough)),
        normalizer,
        meaningNormalizer,
    )

    fun produce(
        card: Card,
        firstExposure: Boolean = false,
        arrived: Boolean = false,
        answer: TurnAnswer = TurnAnswer.Typed,
    ): TurnState = machine.begin(
        card, PresentationRole.Produce,
        turnPrompt(card, PresentationRole.Produce, reviewCount = 0, arrived = arrived),
        firstExposure, arrived, T0, answer,
    )

    /** A hear turn: only an arrived word is ever asked by ear. */
    fun hear(card: Card, answer: TurnAnswer = TurnAnswer.Typed): TurnState = machine.begin(
        card, PresentationRole.Hear,
        turnPrompt(card, PresentationRole.Hear, reviewCount = 0, arrived = true),
        firstExposure = false, arrived = true, nowEpochMillis = T0, answer = answer,
    )

    fun recognize(
        card: Card,
        firstExposure: Boolean = false,
        arrived: Boolean = false,
    ): TurnState = machine.begin(
        card, PresentationRole.Recognize, PromptForm(card.target.text, null),
        firstExposure, arrived, T0,
    )

    fun step(state: TurnState, intent: TurnIntent, nowMillis: Long = T0): TurnReduction =
        machine.reduce(state, intent, nowMillis)

    /** [step] where only the next state matters. */
    fun state(state: TurnState, intent: TurnIntent, nowMillis: Long = T0): TurnState =
        step(state, intent, nowMillis).state
}
