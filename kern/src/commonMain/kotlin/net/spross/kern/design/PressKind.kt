package net.spross.kern.design

/**
 * How far a pressed control shrinks under the thumb, by kind of control.
 * Every press runs the same spring — [RESPONSE] seconds to settle, damped [DAMPING] —
 * so the app has one press, cut a little deeper the smaller the control.
 */
enum class PressKind(val scale: Double) {
    /** A full action button, primary or soft. */
    Action(0.97),

    /** A chip-shaped control: a hub chip, a choice tile, a scramble tile, the listening timer. */
    Chip(0.96),

    /** A grade in the rating row. */
    Rating(0.95),

    /** A compact icon-only button. */
    Icon(0.9);

    companion object {
        const val RESPONSE = 0.25
        const val DAMPING = 0.7
    }
}
