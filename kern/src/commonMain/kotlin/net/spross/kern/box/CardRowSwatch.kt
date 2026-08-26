package net.spross.kern.box

import net.spross.kern.design.Palette
import net.spross.kern.design.Swatch
import net.spross.kern.model.CardPhase

/**
 * Which color this rung wears, decided once here so a row's badge and the shelf's own
 * progress bar — whose segments reference [Palette.amber]/[Palette.success]/[Palette.grown]
 * by these same names — can never disagree about the same rung on either platform.
 */
val CardRowState.Standing.swatch: Swatch
    get() = when {
        consolidated -> Palette.grown
        phase == CardPhase.Review -> Palette.success
        // Learning or Relearning — a Standing row is never New (see [CardRowState.Standing]).
        else -> Palette.amber
    }
