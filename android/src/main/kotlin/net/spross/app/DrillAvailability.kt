package net.spross.app

import net.spross.kern.catalog.alphabet
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.Numbers

/**
 * The platform half of what free practice can offer.
 *
 * Kern answers every drill's availability now ([LetterDrillAvailability] sweeps the catalog
 * and the box, the atlas is a join that either stands or does not); the ONE fact it cannot
 * have is whether this device can say anything in the language, so that is all these hand
 * it. Everything else — the alphabet, the recordings, the settled pool, the countries
 * both sides name — kern reads for itself.
 */

/**
 * What each entry of the roster gates on — the one place a [Drill] meets its condition, so a
 * new drill cannot reach the hub without one.
 */
fun AppModel.offers(drill: Drill): Boolean = when (drill) {
    Drill.Numbers -> numbersOffered
    Drill.Letters -> lettersOffered
    Drill.Countries -> countriesOffered
    Drill.Dates -> datesOffered
    Drill.WordScramble -> wordScrambleOffered
    Drill.SentenceScramble -> sentenceScrambleOffered
    Drill.Opposites -> oppositesOffered
}

/**
 * Whether the hub card belongs on Home at all: the pair has counting content, an alphabet
 * file exists for the target, the atlas joins, the calendars do, or the box itself holds
 * enough to scramble. Any one entry is reason enough.
 *
 * Asked in roster order and answered by the first yes.
 */
val AppModel.trainerHubOffered: Boolean
    get() = Drill.entries.any { offers(it) }

/** Counting, clock and forms all come out of one pack — the registry rule, not the ladder. */
val AppModel.numbersOffered: Boolean
    get() = box?.joinStamp?.target?.let { Numbers.supports(it) } == true

/**
 * The letters entry rides on the alphabet FILE existing and nothing else: the table ships
 * even where the drill cannot run, so a device with no voice still gets the reference sheet.
 */
val AppModel.lettersOffered: Boolean
    get() {
        val language = box?.joinStamp?.target ?: return false
        return catalog?.alphabet(language) != null
    }

/**
 * The Countries entry rides on the JOIN and nothing else — registry by file, exactly as the
 * alphabet's is, and kern is the only judge of it. Audio is no precondition here: the atlas
 * is typed in both directions, so a device with no voice still gets the whole drill.
 */
val AppModel.countriesOffered: Boolean
    get() = atlas != null

/**
 * The Dates entry rides on the JOIN of the two calendars — the atlas rule again, and kern
 * is the only judge of it: a side without a dates file, or a target whose trainer cannot
 * read a day of the month, joins nothing.
 */
val AppModel.datesOffered: Boolean
    get() = dates != null

/**
 * The word scramble rides on the BOX: enough settled words to be worth spelling
 * back out of their own letters. Kern's own floor, read — nothing here counts words.
 * The pool grows as words settle, so it is taken again with the rest of the standing
 * ([BoxNumbers.boxDrills]) rather than decided once at launch, and never per frame.
 */
val AppModel.wordScrambleOffered: Boolean
    get() = numbers.boxDrills.words

/** The sentence scramble rides on the box too: enough phrases unlocked, long enough to reorder. */
val AppModel.sentenceScrambleOffered: Boolean
    get() = numbers.boxDrills.phrases

/** The opposites drill rides on the box too: enough pairs whose both words have arrived. */
val AppModel.oppositesOffered: Boolean
    get() = numbers.boxDrills.opposites

/**
 * What the letter drill can ASK here, freshly swept.
 *
 * OBSERVABLE by construction where it is stored: every fact it reads is state, so the start
 * button turns on by itself the moment `TextToSpeech` finishes binding. Nothing is cached
 * across a foreground — a voice may be installed in Settings while the app sleeps.
 */
fun AppModel.letterReport(): LetterDrillAvailability.Report? {
    val state = box ?: return null
    val cat = catalog ?: return null
    val language = state.joinStamp.target
    return LetterDrillAvailability.report(cat, state, language, pronouncer.canSpeak(language))
}
