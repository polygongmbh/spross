package net.spross.kern.listen

import net.spross.kern.model.EmojiCue
import net.spross.kern.model.emojiCue

import net.spross.kern.catalog.Playback
import net.spross.kern.model.Card
import net.spross.kern.model.fnv1a64

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
 *
 * A floor on the TOTAL a player is left holding, not on the ramp alone — see [fadedGainDb].
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
 * The decibels a player ends up holding for a recording measured at [gainDb], with [fadeDb]
 * of [listeningGainDb]'s ramp over it: the index and the ramp added, and the sum held at
 * [LISTENING_FADE_FLOOR_DB].
 *
 * The floor is on the SUM because that is the number a listener hears. The packs do not
 * share a loudness and the index is what corrects them, so the same ramp lands on a word
 * already 15 dB down and on one playing as it was recorded — and the first crosses the room's
 * own noise floor long before the second. sw, whose phone-plane index is a pack-wide -12 dB,
 * is the one that vanishes: the ramp was reducing every word equally and only sounded like it
 * was singling that pack out (`kern/docs/audio.md`). Floored on the sum, the ramp takes each
 * word as far as the floor and no further, and a run's last minutes are equally quiet rather
 * than equally attenuated.
 *
 * A word whose index already sits under the floor is left where it is: the index is a
 * correction of the shipped bytes and the ramp may decline to deepen it, never undo it.
 * Outside a run [fadeDb] is 0 and this is the clamped index and nothing else — the same
 * number a synthesized utterance takes, whose index is 0 and whose total is the ramp itself.
 */
fun fadedGainDb(gainDb: Double, fadeDb: Double): Double {
    val index = Playback.gainDb(gainDb)
    // why: how much ramp is left before the sum reaches the floor — never positive, so a word
    // already under it takes none at all rather than being lifted back up to it.
    val room = minOf(0.0, LISTENING_FADE_FLOOR_DB - index)
    return index + maxOf(fadeDb, room)
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
 * What a candidate is dealt ALONGSIDE — its [priority] plus what KIND of word it is.
 *
 * The three kinds do not share a lane even at the same number, because only the scheduled
 * one's number is a measurement. New and packed words carry no stability, so their rung is a
 * rate the app chose; sharing rung 4 would let three hundred unseen words crowd out the
 * twenty mid-stability ones that happened to score the same.
 */
private data class ListeningLane(val scheduled: Boolean, val queued: Boolean, val priority: Int)

private fun laneOf(candidate: ListeningCandidate): ListeningLane = ListeningLane(
    scheduled = candidate.scheduled,
    queued = !candidate.scheduled && candidate.queued,
    priority = listeningPriority(candidate),
)

/** `Inventory.seedOrder`'s own tiebreak, reused — the catalog's curriculum, in order. */
private val catalogOrder: Comparator<ListeningCandidate> =
    compareBy({ it.card.seedIndex }, { it.card.id })

/** The hash `Inventory.dueOrder` already de-correlates the box with, for the same reason. */
private val hashedOrder: Comparator<ListeningCandidate> =
    compareBy({ fnv1a64(it.card.id) }, { it.card.id })

private class Dealt(val candidate: ListeningCandidate, val at: Double, val priority: Int)

private val dealOrder: Comparator<Dealt> = compareBy(
    { it.at },
    { -it.priority },
    { it.candidate.card.seedIndex },
    { it.candidate.card.id },
)

/**
 * The playlist: every candidate in the order it will be heard, dealt rather than drawn.
 *
 * Each lane is spread evenly over the whole run — the *n*-th candidate of a lane of priority
 * *p* is placed at `(n + 0.5) / p`, and everything is sorted by that placement. A lane of
 * priority 6 therefore advances six times faster than one of priority 1, which reproduces the
 * old weighted draw's proportions without a die: every lane reaches the ear, the high ones
 * simply reach it more often, and a long run rotates the shaky and packed words back through
 * as it laps.
 *
 * A plain sort by priority is what this exists instead of. It would empty rung 6, then rung 5,
 * then spend the whole run inside a rung-4 block of every unseen word in the catalog — rungs
 * 3, 2 and 1 would never be reached in a session at all.
 *
 * WITHIN a lane the order depends on what the lane is. New and packed words play in strict
 * catalog order, which is the beginner case whole: an empty box is one lane, and it plays the
 * catalog from its very first word. Scheduled words are hashed by card id instead, because a
 * fixed catalog order would let seed neighbors — often related concepts — be heard in the same
 * sequence every run, and a word half-learned from its neighbor is what `Inventory.dueOrder`
 * fights. No clock re-seeds it per day and none is needed: every review moves a word between
 * lanes, so the sequence changes as the box does.
 *
 * Total and deterministic on both platforms: placement, then priority descending, then seed
 * index, then id.
 */
fun listeningOrder(candidates: List<ListeningCandidate>): List<ListeningCandidate> =
    candidates.groupBy(::laneOf)
        .flatMap { (lane, members) ->
            val within = if (lane.scheduled) hashedOrder else catalogOrder
            members.sortedWith(within).mapIndexed { n, candidate ->
                Dealt(candidate, (n + 0.5) / lane.priority, lane.priority)
            }
        }
        .sortedWith(dealOrder)
        .map { it.candidate }
