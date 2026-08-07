package net.spross.kern.trainer

import kotlin.random.Random

/**
 * The clock ladder: five rungs, each a strict SUPERSET of the one below,
 * because a minute the learner has earned is never withdrawn.
 *
 * | 1 | 2 | 3 | 4 | 5 |
 * |---|---|---|---|---|
 * | :00 | + the quarters | + fives to the half | + the whole five-minute grid | any minute |
 *
 * Rung 1 is the hour word and the named hours.
 * Rung 2 is the fraction words, German's register-rich :45 among them.
 * Rung 3 counts up in fives to the half — where German's `halb` pivot at :25 lives.
 * Rung 4 adds the to-the-hour countdown: 35, 40, 50, 55.
 * Rung 5 reads the face out, minute by minute.
 *
 * :45 sits on rung 2 and stays there: at :45 all five languages reach for a dedicated
 * fraction word, while at 35/40/50/55 they all switch to a counted countdown.
 * That contrast is what makes rung 4 a rung of its own rather than rung 3 mirrored;
 * moving :45 up would collapse the two into one.
 *
 * The ladder is language-independent by design.
 * [Trainer.maxLevel] is ObjC-visible, and the "Clock ≥ 3" phrase unlock has to mean
 * the same fraction of the ladder for every pair.
 */
private val RUNGS: List<IntArray> = listOf(
    intArrayOf(0),
    intArrayOf(0, 15, 30, 45),
    intArrayOf(0, 5, 10, 15, 20, 25, 30, 45),
    IntArray(12) { it * 5 },
    IntArray(60) { it },
)

internal val CLOCK_MAX_LEVEL: Int = RUNGS.size

/** The exact minute set rung [level] offers, clamped into the ladder. */
internal fun clockRung(level: Int): IntArray = RUNGS[level.coerceIn(1, CLOCK_MAX_LEVEL) - 1]

/**
 * One minute for [level] — a single draw indexed into the rung's table,
 * so a seeded cross-check against the shared draw machinery stays symmetric
 * with the other kinds, and no rung allocates per call.
 */
internal fun drawClockMinute(level: Int, rng: Random): Int {
    val rung = clockRung(level)
    return rung[rng.nextInt(rung.size)]
}
