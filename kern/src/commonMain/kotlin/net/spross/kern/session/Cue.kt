package net.spross.kern.session

import net.spross.kern.catalog.Chime

/**
 * What a review turn and a drill run alike ask of the platform around a verdict:
 * the beat before moving on, the verdict's sound, and the keyboard.
 * Both effect families carry it ([TurnEffect.Cued], [net.spross.kern.trainer.DrillEffect.Cued]),
 * so each platform carries a cue out in one place.
 */
sealed class Cue {
    /**
     * Arm (or re-arm) the beat before the question moves on.
     * Where VoiceOver, Switch Control or TalkBack runs, a timed change is hostile — it truncates the
     * announcement and moves the page under the user — so the platform skips the timer and renders
     * the explicit button instead, which books the same answer as the beat would.
     * A near miss arms none: its correction is the point of the pause ([TurnFeedback.Almost]).
     */
    data class ArmAdvance(val beat: AdvanceBeat) : Cue()

    data object CancelAdvance : Cue()

    /** Sound the verdict. */
    data class Tone(val kind: ToneKind) : Cue()

    /** A pause that waits for a tap must give the keyboard back, or it covers the button. */
    data object ReleaseFocus : Cue()
}

/**
 * The verdict a cue sounds for, and what the device does about it.
 *
 * A right answer comes all the time, so its feedback is the subtlest there is:
 * the quietest chime of the four and no tap at all.
 * A near miss sounds [Almost], the correct chime's first note alone,
 * while a merged meaning with full credit is cued [Correct] (`TurnMachine.holding`).
 */
enum class ToneKind {
    Correct,
    Almost,
    Wrong,
    Reveal;

    /** The chime the platform plays, at its [Chime.levelDb]. */
    val chime: Chime
        get() = when (this) {
            Correct -> Chime.Correct
            Almost -> Chime.Almost
            Wrong -> Chime.Wrong
            Reveal -> Chime.Reveal
        }

    /** A miss alone taps, with the lightest haptic the device has: a wake-up, never on a hit or a reveal. */
    val taps: Boolean get() = this == Wrong
}
