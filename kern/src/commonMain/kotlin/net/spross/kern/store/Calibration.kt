package net.spross.kern.store

import net.spross.kern.box.BoxState
import net.spross.kern.model.BoxConfig

/**
 * Calibration belongs to the build, not to the document: steps, retention and caps are
 * decisions this version makes, and a box written months ago would otherwise keep
 * answering to the numbers that shipped the day it was created. Every loaded box passes
 * through here, so the stored [BoxConfig] is a record of the past, never an input —
 * growth pacing is the engine's opinion, not a figure the learner tunes.
 *
 * Only the config moves; schedules, queue, counters and own words are the learner's.
 */
fun BoxState.withProductCalibration(): BoxState = copy(config = BoxConfig.product())
