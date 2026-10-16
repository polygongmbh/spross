package net.spross.kern.design

/**
 * How an overview row that just unlocked is marked, timed once for both apps
 * (which rows, and when, is `trainer/DrillUnlockMark`):
 * the padlock stands [HOLD_MS], then fades over [FADE_MS] while shrinking to [PADLOCK_SHRINK],
 * and the wash behind the row, at [WASH_ALPHA] of the accent, fades with it.
 * Each app keeps its native easing curve.
 */
object UnlockMarkMotion {

    /** How long the padlock stands before it goes — past a screen sliding in, so the eye finds it first. */
    const val HOLD_MS: Int = 600

    const val FADE_MS: Int = 900

    /** The scale the fading padlock shrinks to, where motion is not reduced. */
    const val PADLOCK_SHRINK: Double = 0.6

    /** The accent's opacity in the wash over a row that just unlocked. */
    const val WASH_ALPHA: Double = 0.12

    /** The pause before the unlocked rows are announced, so the screen change being announced is not talked over. */
    const val ANNOUNCE_DELAY_MS: Int = 700
}
