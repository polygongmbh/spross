package net.spross.app

import net.spross.app.audio.Pronouncer
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.catalog.utterance
import net.spross.kern.model.Language
import net.spross.kern.trainer.LetterDrillTask
import net.spross.kern.trainer.LetterPromptKind
import net.spross.kern.trainer.LetterStage

/**
 * The drill's audio glue — the twin of [SessionAudio]'s review-loop half, and of
 * `AppModel+Audio.swift`'s letters section on iOS.
 *
 * Kern says WHAT a question is; this turns it into WHAT to say, and the task's own
 * provenance decides which recording — if any — may serve it. Whether a fire is audible
 * (the read-aloud switch, the TalkBack gate) stays in [Pronouncer], never here.
 */

fun AppModel.letterPrompt(task: LetterDrillTask): Pronunciation? = when (task.promptKind) {
    // why: the ONE lookup NOT keyed by the visible form — what is written (р) and what is
    // said («ер») are different strings, so the manifest is addressed by the glyph.
    LetterPromptKind.Name -> task.promptGlyph?.let { glyph ->
        letterName(task.promptText, glyph, task.language)
    }
    // A word the catalog owns, and an `exampleText` that no concept does: both take the
    // matched-form lookup the review cards use. An `exampleText` carries no slug, but the
    // manifest records it by the FORM it speaks (`texts{}`), so the lookup reaches its own
    // recording and can reach no other word's — which is the D2 guarantee itself, not an
    // exception to it. Synthesis is the fallback, as it is for an unrecorded word.
    LetterPromptKind.Word, LetterPromptKind.PlainText ->
        catalog?.pronunciation(task.language, task.promptText)
}

/** Says the question. AUTO is where mute and the TalkBack gate apply; TAP always sounds. */
fun AppModel.playLetterPrompt(task: LetterDrillTask, trigger: Pronouncer.Trigger) {
    val pronunciation = letterPrompt(task) ?: return
    pronouncer.pronounce(pronunciation, trigger)
}

/**
 * The replay action, null where this device can neither play nor speak the prompt — the
 * card then shows a dead speaker rather than pretending. It touches no focus: a keyboard
 * taken away on every replay makes dictation unusable.
 */
fun AppModel.letterReplay(task: LetterDrillTask): (() -> Unit)? {
    val pronunciation = letterPrompt(task) ?: return null
    if (!pronouncer.canPronounce(pronunciation)) return null
    return { pronouncer.pronounce(pronunciation, Pronouncer.Trigger.TAP) }
}

/**
 * The speaker beside a form the drill hands back — the revealed answer, the correction box —
 * through the same form-keyed lookup every other reveal uses ([speakFormOnTap]).
 *
 * The dictated word only. Every other Sprosse answers with a bare GLYPH, and a glyph is not
 * a form anything may be asked to say: the lookup cannot reach the letter-name recording the
 * card's own replay button plays, and a voice reads it "as anything from a spelling alphabet
 * to a pause" (kern `LetterDrillTask`). Those reveals carry no speaker at all, and the replay
 * above stays the one way to hear the question.
 */
fun AppModel.letterSpeaker(task: LetterDrillTask, form: String): (() -> Unit)? =
    if (task.stage == LetterStage.Dictation) speakFormOnTap(form, task.language) else null

/**
 * A letter's own NAME — «ер», never the bare glyph, which a synthesizer reads as anything
 * from a spelling alphabet to a pause. The letters pack answers it where a recording
 * exists; the voice where it does not.
 *
 * why: the whole recording, not just its path — the letters are the quietest and
 * latest-starting files we ship, so the analysis index travels with them.
 */
fun AppModel.letterName(name: String, glyph: String, lang: Language): Pronunciation {
    val recording = catalog?.letterRecording(lang, glyph)
    return Pronunciation(
        form = name,
        utterance = utterance(name),
        lang = lang,
        recordingPath = recording?.path,
        gain = recording?.gain ?: 0.0,
        gainPhone = recording?.gainPhone,
        leadMs = recording?.leadMs ?: 0,
    )
}

