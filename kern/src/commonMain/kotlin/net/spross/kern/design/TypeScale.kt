package net.spross.kern.design

import kotlin.math.min

/**
 * How much larger type stands in a window, on top of the reader's own text size, never instead of it.
 *
 * A window whose shorter side reaches [BIG_WINDOW_SHORT_SIDE] is read from further off and has room to spare,
 * so its type grows: the whole app by [APP], a session's card by [SESSION].
 * Every phone, the largest included, keeps [NORMAL].
 * The shorter side decides, so a big window counts in either orientation,
 * and the rule is apart from whether the window splits into panes ([WindowPanes]).
 *
 * The window's size is points (dp), safe area included.
 */
class TypeScale(windowWidth: Double, windowHeight: Double) {

    val big: Boolean = min(windowWidth, windowHeight) >= BIG_WINDOW_SHORT_SIDE

    /** The factor for the app's type. */
    val app: Double = if (big) APP else NORMAL

    /** The factor for a session's card and what is asked on it, in place of [app]. */
    val session: Double = if (big) SESSION else NORMAL

    companion object {
        /** The shortest side a big window has: past every phone (about 440), short of an unfolded foldable. */
        const val BIG_WINDOW_SHORT_SIDE: Double = 600.0

        const val NORMAL: Double = 1.0

        /** A big window's app type: a little larger. */
        const val APP: Double = 1.12

        /** A big window's session type: the card is the one thing on screen, so it grows most. */
        const val SESSION: Double = 1.25
    }
}
