package net.spross.app.listen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.kern.listen.listeningGainDb
import net.spross.kern.listen.listeningTimerStepMs

/** A listening run's bedtime: when it falls, and kern's ramp toward it. */
class ListeningBedtime {
    /**
     * The stretch the bedtime standing runs over, in milliseconds; 0 is off, which is where a
     * run starts. Every tap resets it — a bedtime extended at midnight is a new stretch, not
     * the old one with a longer tail, so kern's ramp starts over from full.
     */
    var totalMs by mutableStateOf(0L)
        private set

    /** When the bedtime falls, or null while the run laps for as long as it is left alone. */
    var deadline by mutableStateOf<Long?>(null)
        private set

    /**
     * Adds kern's five minutes to WHAT IS LEFT of the bedtime — never to what was picked, so a
     * run four minutes into a five-minute bedtime gets six more and not ten
     * (`listeningTimerStepMs`). Every tap only ever ADDS: the minutes never come down by
     * tapping, and the chip's long press is the one way back to off.
     */
    internal fun extend() {
        totalMs = listeningTimerStepMs(remainingMs() ?: 0L, 1)
        deadline = System.currentTimeMillis() + totalMs
    }

    internal fun clear() {
        totalMs = 0
        deadline = null
    }

    /** How long the bedtime has left, or null where none was set. */
    fun remainingMs(): Long? = deadline?.let { it - System.currentTimeMillis() }

    /** How far the bar on the lock screen stands, or null where no bedtime was set. */
    internal fun progress(): ListeningBedtimeProgress? =
        remainingMs()?.let { ListeningBedtimeProgress(totalMs - it.coerceAtLeast(0L), totalMs) }

    /**
     * Kern's bedtime ramp at this moment: quieter by degrees over the whole run rather than a
     * dimming that starts. The length rides along with what is left of it, so the ramp is a
     * fraction of the bedtime and every length ends in the same place.
     */
    internal fun gainDb(): Double =
        remainingMs()?.let { listeningGainDb(it, totalMs) } ?: 0.0
}
