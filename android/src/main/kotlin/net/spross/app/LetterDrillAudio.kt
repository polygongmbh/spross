package net.spross.app

import net.spross.app.audio.Pronouncer
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.catalog.utterance
import net.spross.kern.trainer.LetterDrillTask
import net.spross.kern.trainer.LetterPromptKind

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
        // why: the whole recording, not just its path — the letters are the quietest and
        // latest-starting files we ship, and the drill is where that is heard, so the
        // analysis index travels with them.
        val recording = catalog?.letterRecording(task.language, glyph)
        Pronunciation(
            form = task.promptText,
            utterance = utterance(task.promptText),
            lang = task.language,
            recordingPath = recording?.path,
            gain = recording?.gain ?: 0.0,
            leadMs = recording?.leadMs ?: 0,
        )
    }
    // A word the catalog owns: the matched-form lookup the review cards use.
    LetterPromptKind.Word -> catalog?.pronunciation(task.language, task.promptText)
    // why: no lookup at all — an `exampleText` carries no slug, and a concept's recording
    // may never play over a different word (D2).
    LetterPromptKind.PlainText -> Pronunciation(
        form = task.promptText,
        utterance = utterance(task.promptText),
        lang = task.language,
        recordingPath = null,
    )
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
