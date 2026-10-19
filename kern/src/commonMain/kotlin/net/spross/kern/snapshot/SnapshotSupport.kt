package net.spross.kern.snapshot

import net.spross.kern.box.BoxState
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.model.Card
import net.spross.kern.model.Gender
import net.spross.kern.model.Language
import net.spross.kern.model.gender

/**
 * The target word's [gender] as the wire spells it (`masculine`/`feminine`/`neuter`) —
 * resolved here because the decode-only surfaces read neither the catalog's authored
 * gender nor which language a shared article belongs to. Null where the box names no gender.
 */
internal fun wireGender(card: Card): String? =
    card.target.gender?.name?.lowercase()

/** The [Gender] a snapshot's wire string names, or null for none (or one this build does not know). */
internal fun genderOf(wire: String?): Gender? = Gender.entries.firstOrNull { it.name.lowercase() == wire }

/**
 * The language the decode-only surfaces write their chrome in: the one the app's own
 * chrome follows ([LanguageChoices.chromeLanguage] of the box's known language), since
 * a widget or a watch cannot ask the phone's model for it.
 */
internal fun chromeLanguage(state: BoxState): Language =
    LanguageChoices.chromeLanguage(state.joinStamp.source)
