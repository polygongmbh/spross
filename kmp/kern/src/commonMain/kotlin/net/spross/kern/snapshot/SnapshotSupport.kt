package net.spross.kern.snapshot

import net.spross.kern.model.Card

/** ♀ badge baked into snapshot strings — snapshot consumers render text verbatim. */
internal const val FEMININE_MARKER = "♀"

/**
 * Source text with the ♀ marker baked in. Contract §3: the marker decorates the
 * SOURCE side wherever it appears (produce prompt, recognize reveal).
 */
internal fun decoratedSourceText(card: Card): String =
    if (card.promptFeminineMarker) "${card.source.text} $FEMININE_MARKER" else card.source.text

/** Article tint only when the TARGET grammar carries gender (contract §7). */
internal fun articleTint(card: Card): String? = card.target.grammar["gender"]
