package net.spross.app

import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.Trainer

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
 * Whether the hub card belongs on Heute at all: the pair has counting content, an alphabet
 * file exists for the target, or the atlas joins. Three entries, any of which is reason
 * enough.
 */
val AppModel.werkstattOffered: Boolean
    get() = numbersOffered || lettersOffered || countriesOffered

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
 * The Länder entry rides on the JOIN and nothing else — registry by file, exactly as the
 * alphabet's is, and kern is the only judge of it. Audio is no precondition here: the atlas
 * is typed in both directions, so a device with no voice still gets the whole drill.
 */
val AppModel.countriesOffered: Boolean
    get() = atlas != null

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
