package net.spross.kern.snapshot

import net.spross.kern.model.Card

/** ♀ badge baked into widget strings — the widget renders text verbatim. */
internal const val FEMININE_MARKER = "♀"

/**
 * Source text with the ♀ marker baked in (widget only — the watch carries a
 * `femMarker` flag instead and renders a labeled badge itself).
 */
internal fun decoratedSourceText(card: Card): String =
    if (card.promptFeminineMarker) "${card.source.text} $FEMININE_MARKER" else card.source.text

/** Article tint only when the TARGET grammar carries gender. */
internal fun articleTint(card: Card): String? = card.target.grammar["gender"]
