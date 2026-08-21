package net.spross.kern.listen

import kotlin.random.Random
import net.spross.kern.model.Card

/**
 * How long a word the learner already HOLDS is left alone before its meaning arrives.
 *
 * The one beat of a listening turn that carries any teaching: a word already answered once
 * can be recalled, and a meaning that lands before the learner has reached for it turns the
 * run into background noise. Kern owns the number so the two phones cannot drift.
 */
const val RECALL_GAP_HELD_MS: Long = 2_500

/**
 * The same beat for a word the learner has never answered.
 *
 * There is nothing to recall on a first hearing, so the gap is a breath rather than a pause —
 * long enough that the two languages do not run together, short enough that a first meeting
 * does not feel like a test the learner is failing.
 */
const val RECALL_GAP_FRESH_MS: Long = 900

/** Between the meaning and the target word said again — the echo that closes a turn. */
const val ECHO_GAP_MS: Long = 1_200

/** Between one turn's last word and the next turn's first. */
const val TURN_GAP_MS: Long = 2_500

/**
 * Below this many scheduled words the pool is topped up with unseen ones (`ListeningPool`).
 *
 * It sits above [RECENCY_WINDOW] on purpose: a pool the size of the window has nothing left
 * to draw from once the window is full and has to lap, so a topped-up pool must clear it.
 */
const val LISTENING_POOL_FLOOR: Int = 12

/** Ceilings on what makes a word worth hearing twice — see [listeningWeight]. */
private const val LAPSE_CAP = 3
private const val DIFFICULTY_CAP = 2

/** FSRS difficulty runs 1–10; below its middle a word is not what the hour is for. */
private const val DIFFICULTY_MIDPOINT = 5.0
private const val DIFFICULTY_PER_STEP = 2.0

/**
 * A word the playlist may say, plus the four things about it the draw and the beats weigh
 * that a [Card] cannot carry.
 *
 * [difficulty] is FSRS's own 1–10 and [lapses] the times this learner has forgotten the word,
 * both read from `CardScheduling` and never re-derived. [suspended] and [scheduled] are the
 * two facts that decide whether those figures may be believed at all: an unscheduled card has
 * no history, and a suspended one's history has already been acted on (see [listeningWeight]).
 */
data class ListeningCandidate(
    val card: Card,
    val difficulty: Double,
    val lapses: Int,
    val suspended: Boolean,
    /** Whether the card carries a schedule — i.e. whether the learner has ever answered it. */
    val scheduled: Boolean,
)

/**
 * How much of the listening draw a candidate is worth. One is the floor every word keeps —
 * nothing is ever excluded from a playlist, only out-drawn — and two things add to it: the
 * LAPSES, and FSRS's DIFFICULTY above the midpoint. Both are capped, so a single leech cannot
 * take the hour over.
 *
 * A SUSPENDED or UNSCHEDULED word keeps the bare floor and earns no boost. The leech rule
 * suspends at two lapses, so a suspended card's figures are exactly the ones that would win
 * every draw — but the box has already decided that word is being pushed outward, and an hour
 * spent on the handful of words the learner gave up on is not what listening is for. It is
 * still worth hearing; it is just not what the hour is about. An unscheduled word has no
 * history to weigh at all, and 0.0 difficulty would otherwise read as "very easy".
 */
fun listeningWeight(candidate: ListeningCandidate): Int {
    if (candidate.suspended || !candidate.scheduled) return 1
    val forgotten = minOf(LAPSE_CAP, maxOf(0, candidate.lapses))
    val hard = minOf(
        DIFFICULTY_CAP,
        ((candidate.difficulty - DIFFICULTY_MIDPOINT) / DIFFICULTY_PER_STEP).toInt().coerceAtLeast(0),
    )
    return 1 + forgotten + hard
}

/**
 * The gap between the target word and its meaning: [RECALL_GAP_HELD_MS] for a word the
 * learner has answered before, [RECALL_GAP_FRESH_MS] for one they have never met.
 *
 * Having a schedule IS having been answered — introduction is the first answer (README §6),
 * and listening answers nothing, so hearing a word a hundred times never moves it across.
 */
fun recallGap(candidate: ListeningCandidate): Long =
    if (candidate.scheduled) RECALL_GAP_HELD_MS else RECALL_GAP_FRESH_MS

/**
 * Cumulative draw over [weights]; identical to a uniform pick where they all match.
 * The shape the letter drill's dictation draw uses, and for the same reason: a weighted
 * pool must stay a pool, never a filter.
 */
internal fun <T> weighted(pool: List<T>, weights: List<Int>, rng: Random): T {
    val total = weights.sum()
    var roll = rng.nextInt(total)
    for ((index, weight) in weights.withIndex()) {
        roll -= weight
        if (roll < 0) return pool[index]
    }
    return pool.last()
}
