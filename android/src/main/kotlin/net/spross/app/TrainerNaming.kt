package net.spross.app

import net.spross.kern.trainer.CountryTaskKind
import net.spross.kern.trainer.DateTaskKind
import net.spross.kern.trainer.DrillModifier
import net.spross.kern.trainer.DrillVariant
import net.spross.kern.trainer.LetterStage
import net.spross.kern.trainer.drillVariantEmoji

/**
 * What kern's drill enums are CALLED to this learner.
 *
 * Kern names the rule and never the rendering, so the face and the wording of a variant,
 * a modifier and a stage live out here — one table, read by the overviews, the score line
 * and the result tile alike, so a run can never be named two things on one page.
 */

fun Chrome.name(variant: DrillVariant): String = when (variant) {
    DrillVariant.Numbers -> trainerSkillNumbers
    DrillVariant.Clock -> trainerVariantClock
    DrillVariant.Phrases -> trainerVariantPhrases
    DrillVariant.Forms -> trainerVariantForms
}

/** Face and name together — how a row, a price and a mixed run's score line all read. */
fun Chrome.badge(variant: DrillVariant): String = "${drillVariantEmoji(variant)} ${name(variant)}"

/**
 * A modifier has no face of its own: it changes every variant alike, so it is named and
 * explained in words.
 */
fun Chrome.name(modifier: DrillModifier): String = when (modifier) {
    DrillModifier.Reverse -> trainerModifierReverse
    DrillModifier.Fast -> trainerModifierFast
    DrillModifier.Mix -> trainerModifierMix
}

fun Chrome.hint(modifier: DrillModifier): String = when (modifier) {
    DrillModifier.Reverse -> trainerModifierReverseHint
    DrillModifier.Fast -> trainerModifierFastHint
    DrillModifier.Mix -> trainerModifierMixHint
}

fun Chrome.name(stage: LetterStage): String = when (stage) {
    LetterStage.ChoiceEasy -> lettersStageChoiceEasy
    LetterStage.ChoiceConfusable -> lettersStageChoiceConfusable
    LetterStage.Typed -> lettersStageTyped
    LetterStage.Dictation -> lettersStageDictation
}

fun Chrome.hint(stage: LetterStage): String = when (stage) {
    LetterStage.ChoiceEasy -> lettersStageChoiceEasyHint
    LetterStage.ChoiceConfusable -> lettersStageChoiceConfusableHint
    LetterStage.Typed -> lettersStageTypedHint
    LetterStage.Dictation -> lettersStageDictationHint
}

/**
 * What an atlas question ASKS. The kind names the rule, and this is the only place it turns
 * into words — none of which names a language, because the field's placeholder says which
 * side is owed.
 */
fun Chrome.countryAsk(kind: CountryTaskKind): String = when (kind) {
    CountryTaskKind.CountryName -> countriesAskCountry
    CountryTaskKind.FlagCountry -> countriesAskFlag
    CountryTaskKind.LanguageName -> countriesAskLanguage
    CountryTaskKind.Nationality -> countriesAskNationality
    CountryTaskKind.SpokenIn -> countriesAskSpokenIn
    CountryTaskKind.SpokenWhere -> countriesAskSpokenWhere
}

/** What a Sprosse of the atlas ladder is called, and the line under it. */
fun Chrome.countrySprosse(sprosse: Int): String = countrySprossen.rowFor(sprosse)

fun Chrome.countrySprosseHint(sprosse: Int): String = countrySprosseHints.rowFor(sprosse)

/**
 * What a dates question ASKS — the atlas rule, one table. The three assembled kinds share
 * one sentence: what changes between them is on the card, not in the ask.
 */
fun Chrome.dateAsk(kind: DateTaskKind): String = when (kind) {
    DateTaskKind.Weekday -> datesAskWeekday
    DateTaskKind.Month -> datesAskMonth
    DateTaskKind.DayOfMonth -> datesAskDay
    DateTaskKind.DayAndMonth, DateTaskKind.FullDate, DateTaskKind.FullDateWithYear -> datesAskDate
}

/**
 * What a Sprosse of the dates ladder is called, from what kern says it ASKS
 * ([net.spross.kern.trainer.DateDrill.kinds]) — the wordings are keyed by KIND because
 * the ladder has no fixed length: a pair without a year pattern skips that row, and the
 * number on screen is the row's own position.
 */
fun Chrome.dateSprosse(kinds: List<DateTaskKind>): String = dateSprossen.rowFor(dateSprosseIndex(kinds))

fun Chrome.dateSprosseHint(kinds: List<DateTaskKind>): String =
    dateSprosseHints.rowFor(dateSprosseIndex(kinds))

/** A Sprosse carries every kind below it, so the LAST one is what it introduced and is named for. */
private fun dateSprosseIndex(kinds: List<DateTaskKind>): Int =
    when (kinds.lastOrNull()) {
        DateTaskKind.Weekday -> 1
        DateTaskKind.Month -> 2
        DateTaskKind.DayOfMonth -> 3
        DateTaskKind.DayAndMonth -> 4
        DateTaskKind.FullDate -> 5
        DateTaskKind.FullDateWithYear -> 6
        null -> 6
    }

/** How far from home a reference group sits — kern hands the tier over already effective. */
fun Chrome.countryTier(tier: Int): String = countryTiers.rowFor(tier)

/**
 * The wording for a 1-based row of a kern-length ladder. A ladder that grew past the table
 * takes the last wording rather than printing nothing: kern is free to add a Sprosse before the
 * chrome has a sentence for it, and a Sprosse with no name at all would be worse than a
 * repeated one — the same fallback the iOS catalog's `default:` case makes.
 */
private fun List<String>.rowFor(index: Int): String =
    getOrNull(index - 1) ?: lastOrNull() ?: ""

/**
 * What a locked row costs, straight out of kern's unlock table — never a price authored
 * beside it, which would go stale the day the table moves.
 *
 * Numbers counts DIGITS and its wording already wears the drill's face, so it prints as
 * the length it is; every other variant names itself and its Sprosse.
 */
fun Chrome.unlockPrice(required: Map<DrillVariant, Int>): String {
    val parts = DrillVariant.entries.mapNotNull { variant ->
        val sprosse = required[variant] ?: return@mapNotNull null
        if (variant == DrillVariant.Numbers) {
            countLine(numbersSprosseOne, numbersSprosse, sprosse)
        } else {
            "${badge(variant)} ${trainerSprosse.format(sprosse)}"
        }
    }
    if (parts.isEmpty()) return numbersUnlock
    return "$numbersUnlock ${parts.joinToString(" · ")}"
}

/**
 * How far an exercise has ever climbed, under its name — the record the atlas and the
 * calendar print under their ladder, said per exercise here because each one climbs its
 * own. Numbers counts DIGITS, exactly as its price does.
 */
fun Chrome.bestSprosse(variant: DrillVariant, sprosse: Int): String =
    if (variant == DrillVariant.Numbers) {
        "$numbersBest ${countLine(numbersSprosseOne, numbersSprosse, sprosse)}"
    } else {
        "$numbersBest ${trainerSprosse.format(sprosse)}"
    }
