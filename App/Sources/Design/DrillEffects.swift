import SprossKern

/// What a drill reduction's effects become on this device. Timers, focus and
/// playback are the platform's; WHICH branch waits and which moves on is the
/// run's rule, which is what the effects say. The three endless drills keep
/// their own state machines — a heard glyph and a typed numeral share no
/// grammar — but the acts they ask of the world outside them are one list,
/// as they are on Android (`DrillActs.carryOut`).
@MainActor
enum DrillEffects {

    /// Carry out one effect. `advance` is the caller's own armed beat, so a
    /// view that cancels or re-arms elsewhere still holds a single task.
    /// `voice` says the graded answer, and `model` is where it looks it up
    /// (nil in a preview, which says nothing).
    static func apply(_ effect: DrillEffect,
                      advance: inout Task<Void, Never>?,
                      voice: AnswerVoice,
                      model: AppModel?,
                      onAdvance: @escaping @MainActor () -> Void,
                      releaseFocus: () -> Void,
                      silence: () -> Void) {
        switch onEnum(of: effect) {
        case .armAdvance(let beat):
            // why: AutoAdvance skips the timer under a screen reader — it
            // truncates the correctness announcement and moves the screen under
            // the user, and the branches render "Weiter" there instead.
            // The beat also waits out the answer being said, or the next
            // question's silence would cut the word off.
            AutoAdvance.schedule(beat.tier, &advance, holding: { await voice.said() },
                                 action: onAdvance)
        case .sayAnswer(let answer):
            guard let model else { return }
            voice.speak(answer.text, lang: answer.language, via: model)
        case .cancelAdvance:
            advance?.cancel()
        case .tone(let cue):
            Sound.play(cue.kind)
        case .releaseFocus:
            // why: a pause that waits for a tap must not hold the keyboard —
            // it covers the button the pause is waiting for.
            releaseFocus()
        case .silence:
            // why: the reading belongs to the question being left.
            silence()
        }
    }
}
