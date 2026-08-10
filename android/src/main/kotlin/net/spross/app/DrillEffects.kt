package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.ToneKind
import net.spross.kern.trainer.DrillEffect

/**
 * The platform half both endless drills share: the beat that is armed and the four acts an
 * effect asks for.
 *
 * The state machines stay apart — a heard glyph and a typed numeral share no grammar — but
 * what kern asks of the world outside them is one list, and a second copy of it is how two
 * beats drift apart.
 */

/**
 * The armed beat before a run moves on.
 *
 * Where a screen reader is reading the screen a timed change is hostile — it truncates the
 * announcement and moves the page under the user — so no beat is ever armed there and
 * [awaitsConfirm] puts an explicit button in its place.
 */
class DrillBeat(private val screenReaderOn: () -> Boolean) {

    /** The tier waiting to elapse; null once it fired or was cancelled. */
    var tier by mutableStateOf<AdvanceTier?>(null)
        private set

    /**
     * Bumped by every arming. What a timer effect keys on: two beats in a row can be the
     * same tier, and a key that compares equal would never fire the second one.
     */
    var token by mutableStateOf(0)
        private set

    var awaitsConfirm by mutableStateOf(false)
        private set

    fun arm(next: AdvanceTier) {
        if (screenReaderOn()) {
            tier = null
            awaitsConfirm = true
            return
        }
        awaitsConfirm = false
        tier = next
        token += 1
    }

    fun cancel() {
        tier = null
        awaitsConfirm = false
    }

    /** The beat elapsed: it is spent whether or not the run had anything to book. */
    fun spend() {
        tier = null
    }
}

/**
 * What a reduction's effects become on this device. Timers, focus and playback are the
 * platform's; WHICH branch waits and which moves on is the run's rule, which is what the
 * effects say.
 */
class DrillActs(
    val beat: DrillBeat,
    /** The verdict's cue — a haptic here, since this app bundles no chimes. */
    private val onTone: (ToneKind) -> Unit,
    /** A pause that waits for a tap must give the keyboard back, or it covers the button. */
    private val onReleaseFocus: () -> Unit,
    /** Cut whatever is sounding: the reading belongs to the question being left. */
    private val onSilence: () -> Unit,
) {
    fun carryOut(effects: List<DrillEffect>) {
        for (effect in effects) {
            when (effect) {
                is DrillEffect.ArmAdvance -> beat.arm(effect.tier)
                DrillEffect.CancelAdvance -> beat.cancel()
                is DrillEffect.Tone -> onTone(effect.kind)
                DrillEffect.ReleaseFocus -> onReleaseFocus()
                DrillEffect.Silence -> onSilence()
            }
        }
    }
}
