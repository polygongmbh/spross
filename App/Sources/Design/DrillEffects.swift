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
    /// `reader` is what the beat waits on while the answer sounds.
    static func apply(_ effect: DrillEffect,
                      advance: inout Task<Void, Never>?,
                      reader: Reader,
                      onAdvance: @escaping @MainActor () -> Void,
                      releaseFocus: () -> Void,
                      silence: () -> Void) {
        switch onEnum(of: effect) {
        case .armAdvance(let arm):
            // why: AutoAdvance skips the timer under a screen reader — it
            // truncates the correctness announcement and moves the screen under
            // the user, and the branches render "Weiter" there instead.
            // The beat also waits out the answer being said, or the next
            // question's silence would cut the word off.
            AutoAdvance.schedule(arm.beat, &advance, holding: { await reader.saidAnswer() },
                                 action: onAdvance)
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
