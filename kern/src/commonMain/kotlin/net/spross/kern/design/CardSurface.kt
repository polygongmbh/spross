package net.spross.kern.design

/**
 * The raised card face both apps draw: its hairline's strength and the shadow under it.
 * The colors stay each app's theme ([Palette.separator] for the hairline, black for the shadow);
 * lengths are points on iOS and dp on Android.
 */
object CardSurface {
    /**
     * The hairline closing the card's edge: the separator at this alpha.
     * Deliberately faint — the fill and the shadow carry the boundary.
     */
    const val HAIRLINE = 0.6

    /** The shadow's black at this alpha: a soft bloom, so the card lifts rather than casting a box. */
    const val SHADOW_ALPHA = 0.08

    /** The shadow's blur radius. */
    const val SHADOW_RADIUS = 16.0

    /** How far below the card the shadow drops. */
    const val SHADOW_Y = 6.0
}
