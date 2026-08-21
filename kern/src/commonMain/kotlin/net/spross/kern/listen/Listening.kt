package net.spross.kern.listen

import net.spross.kern.model.EmojiCue
import net.spross.kern.model.emojiCue

import kotlin.random.Random
import net.spross.kern.model.Card

/**
 * How long a word the learner already HOLDS is left alone before its meaning arrives.
 *
 * The one beat of a listening turn that carries any teaching: a word already answered once
 * can be recalled, and a meaning that lands before the learner has reached for it turns the
 * run into background noise. 1.2 s sits inside the wait-time band teaching materials reach
 * for after a question (Rowe 1974; Stahl 1994) — just past the second a held word takes to
 * retrieve, still quick enough that the playlist moves. Kern owns the number so the two
 * phones cannot drift.
 */
const val RECALL_GAP_HELD_MS: Long = 1_200

/**
 * The same beat for a word the learner has never answered.
 *
 * There is nothing to recall on a first hearing, so the gap is a breath rather than a pause —
 * long enough that the two languages do not run together, short enough that a first meeting
 * does not feel like a test the learner is failing.
 */
const val RECALL_GAP_FRESH_MS: Long = 600

/**
 * Between the meaning and the target word said again — the echo that closes a turn.
 *
 * The echo reuses the FRESH gap rather than minting a beat of its own, so the two pauses of a
 * turn are the two recall gaps the learner's own history already chose.
 */
const val ECHO_GAP_MS: Long = RECALL_GAP_FRESH_MS

/**
 * Between one turn's last word and the next turn's first.
 *
 * The breath between turns reuses the HELD gap, so a playlist keeps one short and one long
 * beat and nothing invents a third.
 */
const val TURN_GAP_MS: Long = RECALL_GAP_HELD_MS

/**
 * How long a beat may wait for a word that never reports its finish.
 *
 * Both audio branches CAN return silently — a file that will not open, a voice that went
 * missing between the pool and the word — and a run that stalls on that silence is worse
 * than one that hurries. The longest reading the catalog holds stays well under this, so it
 * is insurance, never timing. Kern owns it so the two phones cannot decide differently how
 * long they wait.
 */
const val LISTENING_WATCHDOG_MS: Long = 5_000

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
 * The picture cue every listening card wears.
 *
 * A picture is held back while an answer is OWED — it would give it away — and listening owes
 * none: nothing is asked, so nothing is withheld. Named here rather than picked on each phone
 * because that is exactly how the two came to disagree: one held it until the meaning was out
 * and the other until a reveal, and both made the picture vanish and return on every word.
 */
val LISTENING_EMOJI_CUE: EmojiCue = emojiCue(givesAnswerAway = false)

/**
 * Where the fade ends — the level the last word before the bedtime is played at.
 *
 * Roughly a third of the loudness it started at: quiet enough to fall asleep under, loud
 * enough that a learner still awake can follow it. Deeper than this and the run spends its
 * last minutes saying words nobody can hear, which is not a gentler ending, only a longer one.
 */
const val LISTENING_FADE_FLOOR_DB: Double = -19.0

/**
 * The decibels a run is played at, [msRemaining] into a bedtime of [totalMs]: linear from 0.0
 * at the start to [LISTENING_FADE_FLOOR_DB] as it reaches zero.
 *
 * The ramp is the WHOLE bedtime, not a window at the end of it. A fade that only starts near
 * the finish is a second event — the room is steady and then it is dimming — and a listener
 * on the edge of sleep notices a change beginning far more than a level continuing. Spread
 * over the whole run, no single minute is quieter than the one before it by enough to hear.
 *
 * Linear in DECIBELS, which is where a listener's sense of loudness lives; linear in amplitude
 * would spend most of the run near the floor and read as an early drop.
 *
 * Applied ON TOP of a recording's own `Playback.gainDb`, never instead of it — one is a
 * correction of the shipped bytes and this is a deliberate ramp over whatever they play at,
 * and the same number attenuates a synthesized utterance. Both platforms read it here for the
 * same reason they read the beats here: a fade that ran two ramps would be two different
 * bedtimes.
 *
 * Clamped to its OWN floor rather than `Playback.GAIN_LIMIT_DB`: that limit is how far a
 * MEASUREMENT may be trusted, and this is not a measurement — it is a level kern chose.
 * A run with no bedtime never asks ([totalMs] of 0 or less plays at full).
 */
fun listeningGainDb(msRemaining: Long, totalMs: Long): Double {
    if (totalMs <= 0L) return 0.0
    val spent = 1.0 - (maxOf(0L, msRemaining).toDouble() / totalMs).coerceIn(0.0, 1.0)
    // why: `floor * 0.0` is -0.0, which prints and compares as a surprise; + 0.0 normalizes it.
    return (LISTENING_FADE_FLOOR_DB * spent + 0.0).coerceIn(LISTENING_FADE_FLOOR_DB, 0.0)
}

/**
 * Whether the bedtime has arrived. Trivial, and here rather than in two apps because
 * `<= 0` and `< 0` are the same rule until one platform picks the other one.
 */
fun listeningExpired(msRemaining: Long): Boolean = msRemaining <= 0

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
 * history to weigh at all — its 0.0 difficulty is an absence, not a measurement — and with
 * the whole catalog in the pool it reaches the ear by sheer number rather than by a boost.
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
