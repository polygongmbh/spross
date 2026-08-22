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
    val step = steps * LISTENING_TIMER_STEP_MIN * 60_000L
    return maxOf(0L, maxOf(0L, msRemaining) + step)
}

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
 * A word the playlist may say, plus the facts about it the priority reads that a [Card]
 * cannot carry.
 *
 * [stability] is FSRS's own figure in days, read from `CardScheduling` and never re-derived —
 * the whole priority ladder is a function of it. [suspended] and [scheduled] are the two facts
 * that decide whether that figure may be believed at all: an unscheduled card has no history,
 * and a suspended one's history has already been acted on (see [listeningPriority]).
 * [queued] is the learner's own say, which no schedule carries.
 */
data class ListeningCandidate(
    val card: Card,
    /** FSRS stability in days — the whole priority ladder reads off this one figure. */
    val stability: Double,
    val suspended: Boolean,
    /** Whether the card carries a schedule — i.e. whether the learner has ever answered it. */
    val scheduled: Boolean,
    /**
     * Whether the learner PACKED this word (`BoxState.enqueued`) — *these words next*, said
     * before the box got to them. Only ever true of an unscheduled word: packing is answered
     * by introduction, which dequeues.
     */
    val queued: Boolean,
)

/**
 * Where a never-answered word stands, set rather than read — it has no stability to ladder
 * on. A focus tier: a first hearing is the mode's cheapest breadth, and new words are met
 * alongside the ones that are not sticking.
 */
const val LISTENING_NEW_PRIORITY: Int = 4

/**
 * Where a PACKED word stands — one rung above the rest of the unseen ones.
 *
 * Packing is the learner saying *these words next*, and every other surface honors it
 * (`Growth.newCandidates` leads with it, the widget gives it its first tier). One rung is the
 * whole of the ask: it puts the packed words inside the opening turns without letting them
 * lead a word that is actively falling out, which is what the hour is for.
 */
const val LISTENING_QUEUED_PRIORITY: Int = 5

/** The top of the stability ladder — a word at about zero stability (just learned, just lapsed). */
const val LISTENING_MAX_STABILITY_PRIORITY: Int = 6

/** Days of stability a priority point costs — the step of the ladder. */
const val LISTENING_STABILITY_STEP_DAYS: Double = 2.0

/**
 * What being suspended costs a word on the ladder — a toll, not a floor.
 *
 * Two rungs: enough that a leech does not lead the hour, small enough that a shaky one still
 * comes in early. See [listeningPriority] for why a leech is not sent to the back at all.
 */
const val LISTENING_SUSPENDED_PENALTY: Int = 2

/**
 * Where a candidate stands on the listening draw, as one ladder in STABILITY — the higher a
 * word's stability, the lower its place. Higher priority means heard earlier and oftener.
 *
 * A word at zero stability (just learned, or just lapsed back down) is the whole point of
 * the hour and leads; every [LISTENING_STABILITY_STEP_DAYS] of stability costs a point, so
 * the not-quite-settled rotate in the middle and the consolidated ones are pushed to the
 * end — at the bare floor, still worth hearing, never what the hour is about.
 *
 * An UNSCHEDULED word has no stability to read, so its rung is set: [LISTENING_NEW_PRIORITY],
 * or [LISTENING_QUEUED_PRIORITY] where the learner packed it.
 *
 * A SUSPENDED word keeps the rung its stability earned, less [LISTENING_SUSPENDED_PENALTY].
 * A hard floor would contradict `ListeningPool`, which puts leeches in the pool precisely
 * because they are what an hour of listening is FOR: the leech rule takes a word out of the
 * box's rotation, and this is the surface that can still reach it. So a shaky leech lands at
 * rung 3 or 4 — it comes in, it does not lead.
 */
fun listeningPriority(candidate: ListeningCandidate): Int {
    if (!candidate.scheduled) {
        return if (candidate.queued) LISTENING_QUEUED_PRIORITY else LISTENING_NEW_PRIORITY
    }
    val rung = LISTENING_MAX_STABILITY_PRIORITY - (candidate.stability / LISTENING_STABILITY_STEP_DAYS).toInt()
    val tolled = if (candidate.suspended) rung - LISTENING_SUSPENDED_PENALTY else rung
    return tolled.coerceIn(1, LISTENING_MAX_STABILITY_PRIORITY)
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
