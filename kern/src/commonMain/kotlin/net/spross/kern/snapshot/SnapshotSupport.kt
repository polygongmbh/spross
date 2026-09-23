package net.spross.kern.snapshot

import net.spross.kern.box.BoxState
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.model.Card
import net.spross.kern.model.Gender
import net.spross.kern.model.Language
import net.spross.kern.model.articleGender

/** ♀ badge baked into widget strings — the widget renders text verbatim. */
internal const val FEMININE_MARKER = "♀"

/**
 * Source text with the ♀ marker baked in (widget only — the watch carries a
 * `femMarker` flag instead and renders a labeled badge itself).
 */
internal fun decoratedSourceText(card: Card): String =
    if (card.promptFeminineMarker) "${card.source.text} $FEMININE_MARKER" else card.source.text

/** The article the TARGET is shown with, or null where its grammar carries none. */
internal fun article(card: Card): String? = card.target.grammar["gender"]

/**
 * The gender [article] marks in the target's own language, as the wire spells it
 * (`masculine`/`feminine`/`neuter`) — resolved here because the decode-only surfaces
 * cannot tell fr `le` from it `le`. Null where the box names no gender.
 */
internal fun wireGender(card: Card): String? =
    articleGender(article(card), card.target.lang)?.name?.lowercase()

/** The [Gender] a snapshot's wire string names, or null for none (or one this build does not know). */
internal fun genderOf(wire: String?): Gender? = Gender.entries.firstOrNull { it.name.lowercase() == wire }

/**
 * The language the decode-only surfaces write their chrome in: the one the app's own
 * chrome follows ([LanguageChoices.chromeLanguage] of the box's known language), since
 * a widget or a watch cannot ask the phone's model for it.
 */
internal fun chromeLanguage(state: BoxState): Language =
    LanguageChoices.chromeLanguage(state.joinStamp.source)
