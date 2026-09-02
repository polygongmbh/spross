package net.spross.app

import net.spross.app.audio.Pronouncer
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.model.Language

/**
 * What the drills say out loud, outside the letter drill's own prompts.
 *
 * A drill card is a review card, so its revealed reading is spoken and replayable like any
 * other answer (`docs/read-aloud.md`) — and the alphabet sheet, being reading matter rather
 * than a run, sounds only on a tap and then always.
 */

/** The form-keyed lookup, so a recording only ever plays over the word it actually says. */
fun AppModel.formPronunciation(form: String, lang: Language): Pronunciation? =
    catalog?.pronunciation(lang, form)

/**
 * A tap that sounds, null where this device can neither play nor say the form — the
 * speaker is then absent rather than dead.
 *
 * TAP, always: an explicit request is heard even while reading aloud is switched off,
 * exactly as tapping a word on a review card is, and nobody opens a reference sheet by
 * accident.
 */
fun AppModel.speakOnTap(pronunciation: Pronunciation?): (() -> Unit)? {
    if (pronunciation == null || !pronouncer.canPronounce(pronunciation)) return null
    return { pronouncer.pronounce(pronunciation, Pronouncer.Trigger.TAP) }
}

/** The same for a plain form — what a drill card's revealed reading offers. */
fun AppModel.speakFormOnTap(form: String, lang: Language): (() -> Unit)? =
    speakOnTap(formPronunciation(form, lang))

/**
 * Says a drill's revealed reading. AUTO, so the read-aloud switch and the TalkBack gate
 * both apply without this asking about either.
 */
fun AppModel.speakDrillAnswer(form: String, lang: Language) {
    val pronunciation = formPronunciation(form, lang) ?: return
    pronouncer.pronounce(pronunciation, Pronouncer.Trigger.AUTO)
}
