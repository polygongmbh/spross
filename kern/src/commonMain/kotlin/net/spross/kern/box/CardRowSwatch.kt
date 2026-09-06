package net.spross.kern.box

import net.spross.kern.design.Palette
import net.spross.kern.design.Swatch

/**
 * Which color this Sprosse wears, decided once here so a row's badge and the shelf's own
 * progress bar — whose segments reference [Palette.amber]/[Palette.success]/[Palette.grown]
 * by these same names — can never disagree about the same Sprosse on either platform.
 */
val CardRowState.Standing.swatch: Swatch
    get() = when (stage) {
        GrowthStage.Matured -> Palette.grown
        GrowthStage.Growing -> Palette.success
        else -> Palette.amber // Learning, Fresh, Relearning
    }
