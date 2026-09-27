package net.spross.kern.catalog

/**
 * The share of the device's output volume range at or below which a word is too quiet to be
 * heard: a tenth, so the slider's bottom step or two counts on a phone with sixteen steps
 * or twenty-five.
 */
const val LOW_VOLUME_FRACTION: Double = 0.1

/**
 * Whether a screen about to play a word asks for the volume to be turned up.
 * [fraction] is the device's output volume over its whole range, 0 for muted and 1 for full;
 * reading it is each platform's, the line it is held to is this.
 */
fun isVolumeLow(fraction: Double): Boolean = fraction <= LOW_VOLUME_FRACTION
