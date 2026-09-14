package net.spross.app

import net.spross.kern.catalog.alphabet
import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.SentenceScrambleAvailability
import net.spross.kern.trainer.Trainer
import net.spross.kern.trainer.WordScrambleAvailability

/**
 * The platform half of what free practice can offer.
 *
 * Kern answers every drill's availability now ([LetterDrillAvailability] sweeps the catalog
 * and the box, the atlas is a join that either stands or does not); the ONE fact it cannot
 * have is whether this device can say anything in the language, so that is all these hand
 * it. Everything else — the alphabet, the recordings, the consolidated pool, the countries
 * both sides name — kern reads for itself.
 */

/**
 * Whether the hub card belongs on Home at all: the pair has counting content, an alphabet
 * file exists for the target, the atlas joins, the calendars do, or the box itself holds
 * enough to scramble. Any one entry is reason enough.
 *
 * The two scrambles stand LAST because each is a walk of the whole join: a profile with any
 * of the four cheap entries never pays for them.
 */
val AppModel.werkstattOffered: Boolean
    get() = numbersOffered || lettersOffered || countriesOffered || datesOffered ||
        wordScrambleOffered || sentenceScrambleOffered

/** Counting, clock and forms all come out of one pack — the registry rule, not the ladder. */
val AppModel.numbersOffered: Boolean
    get() = box?.joinStamp?.target?.let { Trainer.supports(it) } == true

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
 * The word scramble rides on the BOX: enough words grown far enough to be worth spelling
 * back out of their own letters. Kern's own floor, read — nothing here counts words.
 *
 * Deliberately uncached: the pool grows as words consolidate, so the card asks again rather
 * than deciding once at launch that the drill is empty. It walks the whole join, so the
 * caller asks once per box and not once per frame.
 */
val AppModel.wordScrambleOffered: Boolean
    get() = box?.let { WordScrambleAvailability.drillExists(it) } == true

/**
 * The sentence scramble rides on the box too: enough phrases unlocked, and long enough to
 * have a word order worth putting back. Kern's own floor, read — and the same walk of the
 * whole join its word sibling pays for, so it is asked once per box.
 */
val AppModel.sentenceScrambleOffered: Boolean
    get() = box?.let { SentenceScrambleAvailability.drillExists(it) } == true

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
