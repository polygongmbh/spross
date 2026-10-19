package net.spross.kern.session

/**
 * What a review turn and a drill run alike ask of the platform around a verdict:
 * the beat before moving on, the verdict's sound, and the keyboard.
 * Both effect families carry it ([TurnEffect.Cued], [net.spross.kern.trainer.DrillEffect.Cued]),
 * so each platform carries a cue out in one place.
 */
sealed class Cue {
    /**
     * Arm (or re-arm) the beat before the question moves on.
     * Where a screen reader runs a timed change is hostile — it truncates the announcement and
     * moves the page under the user — so the platform skips the timer and renders the explicit
     * button instead, which books the same answer as the beat would.
     */
    data class ArmAdvance(val beat: AdvanceBeat) : Cue()

    data object CancelAdvance : Cue()

    /** Sound the verdict. */
    data class Tone(val kind: ToneKind) : Cue()

    /** A pause that waits for a tap must give the keyboard back, or it covers the button. */
    data object ReleaseFocus : Cue()
}
