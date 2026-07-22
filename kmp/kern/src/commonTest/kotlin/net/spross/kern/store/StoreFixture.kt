package net.spross.kern.store

import net.spross.kern.box.Box
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Rating
import net.spross.kern.model.Realization

/**
 * Deterministic, feature-dense box for codec round-trip tests and the pinned golden
 * document: learning and review phases, an enqueued phrase, a folded day, and
 * non-ASCII text on both sides.
 */
internal object StoreFixture {

    val cards: List<Card> = listOf(
        Card(
            id = "kueche/kuehlschrank", kind = CardKind.Noun, area = "kueche",
            emoji = "🧊", seedIndex = 0, components = emptyList(), feminineOf = null,
            source = Realization("de", "Kühlschrank", grammar = mapOf("gender" to "der")),
            target = Realization("uk", "холодильник", synonyms = listOf("рефрижератор")),
            promptFeminineMarker = false,
        ),
        Card(
            id = "kueche/kochen", kind = CardKind.Verb, area = "kueche",
            emoji = null, seedIndex = 1, components = emptyList(), feminineOf = null,
            source = Realization("de", "kochen"),
            target = Realization("uk", "готувати", variants = listOf("варити")),
            promptFeminineMarker = false,
        ),
        Card(
            id = "kueche/der-kuehlschrank-ist-leer", kind = CardKind.Phrase, area = "kueche",
            emoji = null, seedIndex = 2,
            components = listOf("kueche/kuehlschrank", "kueche/kochen"), feminineOf = null,
            source = Realization("de", "Der Kühlschrank ist leer."),
            target = Realization("uk", "Холодильник порожній."),
            promptFeminineMarker = false,
        ),
    )

    val stamp = JoinStamp("de", "uk", "store-fixture")

    /** Real engine answers (learning → review, a lapse-free retry), a queue, one folded day. */
    fun state(): BoxState {
        var s = BoxEngine.bootstrap(cards, BoxConfig(), stamp)
        val fridge = "kueche/kuehlschrank"
        s = Box.answered(s, fridge, Rating.Good, Box.day1)
        s = Box.answered(s, fridge, Rating.Good, Box.plusSeconds(Box.day1, 600))
        s = Box.answered(s, "kueche/kochen", Rating.Again, Box.day1)
        s = BoxEngine.enqueue(s, listOf("kueche/der-kuehlschrank-ist-leer"))
        return BoxEngine.endSession(s, reviewsDone = 3, Box.plusSeconds(Box.day1, 3600), Box.TZ)
    }
}
