package net.spross.app

import net.spross.app.audio.Pronouncer
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.model.PronunciationCue
import net.spross.kern.model.pronunciationCue

/**
 * The review loop's audio glue, kept beside the model rather than in it: what a card
 * may say is [SessionUi.promptPronunciation] and Kern's cue, whether it may be heard
 * is [Pronouncer], and all that is left — which form, on which transition — is here.
 *
 * The iOS twin is `SessionView+Audio.swift`; the firing table both follow is
 * design.md § Review UX.
 */

/**
 * How long a produce fire waits after its transition. The correct/wrong/reveal chime
 * is never ducked or shortened for the word, so the word steps around it instead of
 * talking over its own first syllable. Nothing waits on the word in turn — these are
 * the paths that carry no auto-advance at all.
 */
const val CHIME_CLEARANCE_MS = 300L

/**
 * Says the recognition prompt at card mount. Silent by construction on a produce
 * card: the model only fills [SessionUi.promptPronunciation] where Kern's cue has
 * the target on screen from frame one.
 */
fun AppModel.autoplayPrompt() {
    val pronunciation = sessionUi?.promptPronunciation ?: return
    pronouncer.pronounce(pronunciation, Pronouncer.Trigger.AUTO)
}

/**
 * Says one form of the card in play. An AUTO fire is gated on the cue standing at
 * `OnReveal` — never on a role test of its own — so nothing can autoplay a target
 * the learner still owes; a TAP is a request and passes either way.
 */
fun AppModel.pronounceTarget(form: String, trigger: Pronouncer.Trigger) {
    if (trigger == Pronouncer.Trigger.AUTO && !awaitsReveal()) return
    val pronunciation = pronunciationOf(form) ?: return
    pronouncer.pronounce(pronunciation, trigger)
}

/**
 * Tap-to-replay for [form], null where the device can neither play nor speak it —
 * a word that cannot be heard grows no gesture that does nothing. The hit area on
 * the card stands either way.
 */
fun AppModel.pronounceAction(form: String): (() -> Unit)? {
    val pronunciation = pronunciationOf(form) ?: return null
    if (!pronouncer.canPronounce(pronunciation)) return null
    // why: a tap speaks even while reading aloud is switched off — mute has to stay
    // usable as the accessibility affordance, and the About row's hint says so.
    return { pronouncer.pronounce(pronunciation, Pronouncer.Trigger.TAP) }
}

private fun AppModel.pronunciationOf(form: String): Pronunciation? {
    val lang = sessionUi?.card?.target?.lang ?: return null
    return catalog?.pronunciation(lang, form)
}

private fun AppModel.awaitsReveal(): Boolean {
    val role = sessionUi?.role ?: return false
    return pronunciationCue(role) == PronunciationCue.OnReveal
}
