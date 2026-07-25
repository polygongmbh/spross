package net.spross.kern.snapshot

import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.Realization

/** Card/state builders for snapshot tests — richer realizations than the box fixtures. */
internal object Snap {

    fun card(
        id: String,
        seed: Int,
        kind: CardKind = CardKind.Noun,
        emoji: String? = null,
        sourceText: String = "s-$id",
        targetText: String = "t-$id",
        synonyms: List<String> = emptyList(),
        variants: List<String> = emptyList(),
        gender: String? = null,
        feminineMarker: Boolean = false,
    ): Card = Card(
        id = id, kind = kind, area = "area1", emoji = emoji, seedIndex = seed,
        components = emptyList(), feminineOf = null,
        source = Realization("de", sourceText),
        target = Realization(
            "sw", targetText, synonyms = synonyms, variants = variants,
            grammar = if (gender != null) mapOf("gender" to gender) else emptyMap(),
        ),
        promptFeminineMarker = feminineMarker,
    )

    fun state(cards: List<Card>): BoxState =
        BoxEngine.bootstrap(cards, BoxConfig(), JoinStamp("de", "sw", "snap"))
}
