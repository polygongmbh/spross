package net.spross.kern.listen

private const val MINUTE_MS = 60_000L

/**
 * The sleep-timer step: every tap on the bedtime chip adds this many minutes, starting
 * from 0 (OFF, the default, where the playlist laps for as long as it is left alone) —
 * so a bedtime can be had at any multiple of five, and a long press jumps straight back
 * to OFF. Kern owns the number so the two phones step the same way, and so the shape
 * stays one chip rather than a picker: the ask is "let it run while I fall asleep", and
 * a tap is the whole gesture that answer needs.
 */
const val LISTENING_TIMER_STEP_MIN: Int = 5

/**
 * What a tap on the bedtime chip leaves standing, in milliseconds: kern's step added to what
 * is LEFT of the timer, never to what was picked.
 *
 * The difference is the whole gesture. A chip that re-anchored on the pick would give a run
 * five minutes in and tapped again its original five plus five — ten minutes from the tap,
 * not the five more the tap asked for — and the longer the run had gone the further the two
 * readings drift apart. What a learner reaching for it at midnight means is "keep going a bit
 * longer than you were about to", and that is arithmetic on the REMAINDER.
 *
 * [steps] is signed, so the accessible picker walks back down the same ladder it walked up,
 * and a step past the end lands on 0 — OFF, where the playlist laps for as long as it is
 * left alone. Kern owns it so a bedtime cannot mean two things on two phones.
 */
fun listeningTimerStepMs(msRemaining: Long, steps: Int): Long {
    val step = steps * LISTENING_TIMER_STEP_MIN * MINUTE_MS
    return maxOf(0L, maxOf(0L, msRemaining) + step)
}

/**
 * The bedtime chip's reading: whole minutes left, rounded UP,
 * so it never reads zero while a word is still playing.
 *
 * Minutes rather than seconds, because a ticking clock is a clock you watch,
 * which is the opposite of what a sleep timer is for.
 * Never capped — every tap adds another step, and the long press is the only way back down.
 */
fun listeningTimerMinutes(msRemaining: Long): Int =
    ((maxOf(0L, msRemaining) + MINUTE_MS - 1) / MINUTE_MS).toInt()

/**
 * How long the chip's minute still stands, [msRemaining] before the bedtime:
 * what is left, less the whole minutes still left once it turns.
 *
 * The app sleeps this long and reads the chip again, so nothing redraws between minutes.
 * On the last minute it is the whole remainder, so the final wake IS the bedtime;
 * the 50 ms floor keeps a clock that overshot from spinning.
 */
fun listeningTimerWakeMs(msRemaining: Long): Long {
    val whole = maxOf(listeningTimerMinutes(msRemaining) - 1, 0)
    return maxOf(msRemaining - whole * MINUTE_MS, 50L)
}
