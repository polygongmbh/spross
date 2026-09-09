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
 * The decibels a player ends up holding for a recording measured at [gainDb] whose peak
 * ceiling held [capDb] back, with [fadeDb] of [listeningGainDb]'s ramp over it: the index and
 * the ramp added, the sum held at [LISTENING_FADE_FLOOR_DB], and as much of the cap handed
 * back as the ramp has taken off.
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
 *
 * The CAP is the other half of the same drift. The converter holds a boost to the headroom
 * its own file has, so a quiet word with sharp peaks ships under the loudness target rather
 * than distorting — 5-25% of every pack but sw, which is loud and is never capped. That
 * ceiling is only true at full volume: the ramp attenuates ahead of the boost and opens the
 * headroom again, so exactly as much of the deficit as the ramp has taken off may be handed
 * back, and no more — the output peak is where it always was. Outside a run the ramp is 0,
 * nothing is handed back, and this is the clamped index and nothing else — the same number a
 * synthesized utterance takes, whose index and cap are 0 and whose total is the ramp itself.
 */
fun fadedGainDb(gainDb: Double, capDb: Double, fadeDb: Double): Double {
    val index = Playback.gainDb(gainDb)
    // why: how much ramp is left before the sum reaches the floor — never positive, so a word
    // already under it takes none at all rather than being lifted back up to it.
    val room = minOf(0.0, LISTENING_FADE_FLOOR_DB - index)
    val fade = maxOf(fadeDb, room)
    // why: the headroom the ramp actually opened, never the ramp it was asked for — a word
    // the floor held back never attenuated that far and has no ceiling to spend.
    return index + fade + minOf(maxOf(0.0, capDb), -fade)
}


/**
 * A word the playlist may say, plus the facts about it the deal reads that a [Card]
 * cannot carry.
 *
 * [growing] is the box's own bar, read from `CardScheduling` and never re-derived — the whole
 * ladder is a function of it. [suspended] and [scheduled] are the two facts that decide whether
 * it may be read at all: an unscheduled card has no history, and a suspended one's history has
 * already been acted on (see [listeningPriority]). [queued] is the learner's own say, which no
 * schedule carries.
 */
data class ListeningCandidate(
    val card: Card,
    /**
     * Whether the word has cleared the box's growing bar (`Statistics.isGrowing`) — the one
     * reading the ladder takes. Read from the box rather than re-derived from a stability, so
     * a lapsed word is shaky here exactly as it is everywhere else, whatever it once reached.
     */
    val growing: Boolean,
    val suspended: Boolean,
    /** Whether the card carries a schedule — i.e. whether the learner has ever answered it. */
    val scheduled: Boolean,
    /**
     * Whether the learner PACKED this word (`BoxState.enqueued`) — *these words next*, said
     * before the box got to them. Only ever true of an unscheduled word: packing is answered
     * by introduction, which dequeues.
     */
    val queued: Boolean,
    /**
     * Position in `Growth.enqueuedEligible`'s own order, most recently packed first (0 = just
     * packed) — meaningful only where [queued] is true; ignored otherwise.
     */
    val packedRank: Int,
)

/**
 * The share of a run's turns that go to words the learner has never answered — two in five,
 * from the very first turn, whatever the box holds.
 *
 * Audio is the cheapest exposure a word never met can get: nothing is asked, nothing is owed,
 * and a first hearing costs a few seconds. So the unseen words are not a Sprosse of the ladder
 * but a fixed slice beside it — the shakiest words own the opening, and every second or third
 * turn is still a word the learner has not met.
 */
const val LISTENING_NEW_SHARE: Double = 0.4

/**
 * How many turns a held word waits, at the least, before it is said again.
 *
 * A lane's words come back as often as its share and its size allow, so a box holding two
 * shaky words would otherwise say one of them every other turn all evening. Thirty turns is
 * a few minutes — long enough that a return is a return, not an echo — and the turns a small
 * lane cannot fill fall to whichever lane is next.
 */
const val LISTENING_RETURN_FLOOR_TURNS: Int = 30

/** The top of the listening ladder: a scheduled word still short of the growing bar. */
const val LISTENING_SHAKY_PRIORITY: Int = 2

/** The ladder's floor: a word past the growing bar, or a suspended one. */
const val LISTENING_GROWING_PRIORITY: Int = 1

/**
 * Where a scheduled word stands on the listening ladder — two Sprossen, read off the box's own
 * bar rather than a ladder of listening's own.
 *
 * A word short of `growingStability` (`Statistics.isGrowing`, so a lapsed word is shaky whatever
 * it once reached) leads: it is the whole point of the hour. A word past it is still worth
 * hearing, and takes the floor. A fully grown word is not on the ladder at all — `ListeningPool`
 * leaves it out — so the floor is the growing band, not a dumping ground.
 *
 * A SUSPENDED word takes the floor whatever its bar. `ListeningPool` keeps leeches in the pool
 * because they are what an hour of listening is for, and this is the surface that can still
 * reach them — but a word the box has given up on does not lead the hour over the ones it is
 * still working on: it comes in, it does not lead.
 */
fun listeningPriority(growing: Boolean, suspended: Boolean): Int =
    if (growing || suspended) LISTENING_GROWING_PRIORITY else LISTENING_SHAKY_PRIORITY

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
 * The packed lane's own within-lane order: most recently packed first ([ListeningCandidate.packedRank]),
 * the same order `Growth.enqueuedEligible` introduces them in. Packing is the learner naming
 * an explicit order — *this one next* — so a shuffle would be second-guessing it, and what
 * they packed last is the freshest ask, ahead of an older one still waiting in the queue.
 */
private val packedOrder: Comparator<ListeningCandidate> =
    compareBy({ it.packedRank }, { it.card.id })

/**
 * The hash `Inventory.dueOrder` already de-correlates the box with, for the same reason —
 * salted with [seed] so a run dealt with a different one reshuffles instead of replaying the
 * same sequence: the apps already re-sweep the pool on every foreground and hand in the
 * current instant, so this alone gives a learner who listens more than once a day a fresh
 * order each time, without kern reading a clock of its own — kern only ever sees the number,
 * never where it came from.
 */
private fun hashedOrder(seed: Long): Comparator<ListeningCandidate> =
    compareBy({ fnv1a64("$seed:${fnv1a64(it.card.id)}") }, { it.card.id })

/**
 * How many of the catalog's earliest concepts still count as "just starting out" — greetings,
 * thanks, the everyday conversational turns. A session opened on a fresh box leads with this
 * stretch before the pool opens up, so a learner who has never met the language still hears
 * hello before anything forty shelves in.
 *
 * Past it, breadth is the whole point again and any unseen word may lead ([newWordOrder]).
 */
private const val LISTENING_BASICS_WORDS = 50

/**
 * The plain new lane's own within-lane order — not strict catalog order, which used to pin a
 * single word (the earliest unseen one) to the very front of every sweep until growth actually
 * reached it: a box that packs faster than it grows, or simply grows slowly, could leave that
 * one word leading every session for weeks, which is a queue of one where listening promises a
 * stream.
 *
 * Split in two: the [LISTENING_BASICS_WORDS] earliest concepts first, everything else after —
 * each half hashed and salted with [seed] exactly like [hashedOrder], so neither half repeats
 * the same leader twice. The split keeps the one thing catalog order was actually for — an
 * empty box still opens on greetings, not on a word from deep in the catalog — without holding
 * any single word at the front, or the basics to a fixed sequence among themselves: they only
 * need to lead as a group, not to arrive named in seed order.
 */
private fun newWordOrder(seed: Long): Comparator<ListeningCandidate> = compareBy(
    { it.card.seedIndex >= LISTENING_BASICS_WORDS },
    { fnv1a64("$seed:${fnv1a64(it.card.id)}") },
    { it.card.id },
)

/**
 * One lane of the deal: its words in their within-lane order, how far it has walked, and when
 * it is next due on the deal's clock. The held lanes CYCLE — after their first pass they start
 * over at their head — and the unseen lane is spent once it has said every word once.
 */
private class Lane(val members: List<ListeningCandidate>, val priority: Int, val cycles: Boolean) {
    var cursor: Int = 0
    var nextAt: Double = 0.0
    var open: Boolean = false
    val next: ListeningCandidate get() = members[cursor % members.size]
    val firstPassDone: Boolean get() = cursor >= members.size
    val spent: Boolean get() = !cycles && firstPassDone
}

/**
 * The playlist: the sequence a run walks, dealt turn by turn rather than sorted.
 *
 * The shaky lane opens at the first turn and plays every word it holds before the growing
 * lane opens at all — a learner with plenty of words slipping hears those, not a word the box
 * already trusts. From then on both are open, splitting the held turns by Sprosse
 * ([listeningPriority], two to one), and each lane starts over at its own head when it runs
 * out: no word comes back before the rest of its Sprosse has, and the fewer words a Sprosse
 * holds the sooner each of them returns. [LISTENING_RETURN_FLOOR_TURNS] is the one brake — a
 * word said that recently waits, and the turn goes to whichever lane is next.
 *
 * The unseen lane is not a Sprosse but a fixed slice, [LISTENING_NEW_SHARE] of the turns from
 * the very first one, and it closes once every unseen word has been said once. Each open lane
 * is due every `1 / share` turns on one shared clock; the earliest due plays, a tie going to
 * the higher Sprosse. The deal ends when every held Sprosse has played through once and the
 * unseen lane is spent, and the run laps it from the head — so a box holding nothing scheduled
 * hears its unseen words once through, basics first.
 *
 * WITHIN a lane the order depends on what the lane is. Packed words lead the unseen lane,
 * most-recently-packed first ([packedOrder]) — the same order `Growth.enqueuedEligible`
 * introduces them in, so listening and review agree on which packed word is next. Packing
 * named its own order, so this one never reshuffles with [seed]: a shuffle would be
 * second-guessing the learner's own ask. Plain new words split basics-first, then hashed
 * within each half ([newWordOrder]): an empty box still opens on greetings, but no single word
 * is pinned to the front forever. Scheduled words are hashed by card id outright, because a
 * fixed catalog order would let seed neighbors — often related concepts — be heard in the same
 * sequence every run, and a word half-learned from its neighbor is what `Inventory.dueOrder`
 * fights. Both the scheduled and plain-new hashes are salted with [seed], so their sequence
 * also changes between two dealings of an otherwise-unchanged box. [seed] is opaque to kern —
 * it only folds the number into the hash, so whatever a caller hands in (the current instant,
 * today) is theirs to choose.
 *
 * Total and deterministic FOR ONE SEED on both platforms.
 */
fun listeningOrder(candidates: List<ListeningCandidate>, seed: Long): List<ListeningCandidate> {
    val (scheduled, unseen) = candidates.partition { it.scheduled }
    val ladder = scheduled.groupBy { listeningPriority(it.growing, it.suspended) }
        .entries.sortedByDescending { it.key }
        .map { (priority, members) -> Lane(members.sortedWith(hashedOrder(seed)), priority, cycles = true) }
    val (packed, plain) = unseen.partition { it.queued }
    val fresh = Lane(packed.sortedWith(packedOrder) + plain.sortedWith(newWordOrder(seed)), priority = 0, cycles = false)
    val lanes = ladder + fresh

    fun step(lane: Lane): Double {
        val held = ladder.filter { it.open }
        if (lane === fresh) return if (held.isEmpty()) 1.0 else 1.0 / LISTENING_NEW_SHARE
        val heldShare = if (fresh.open) 1.0 - LISTENING_NEW_SHARE else 1.0
        return held.sumOf { it.priority } / (heldShare * lane.priority)
    }

    var clock = 0.0
    fun open(lane: Lane) {
        lane.open = true
        lane.nextAt = clock + step(lane) / 2
    }
    ladder.firstOrNull()?.let(::open)
    if (fresh.members.isNotEmpty()) open(fresh)

    val playlist = mutableListOf<ListeningCandidate>()
    val lastSaid = mutableMapOf<String, Int>()
    while (!(ladder.all { it.firstPassDone } && fresh.spent)) {
        // A first pass never repeats, and the unseen lane never does, so something is always due.
        val lane = lanes
            .filter { it.open && playlist.size - (lastSaid[it.next.card.id] ?: Int.MIN_VALUE / 2) >= LISTENING_RETURN_FLOOR_TURNS }
            .minWith(compareBy({ it.nextAt }, { -it.priority }))
        val said = lane.next
        lastSaid[said.card.id] = playlist.size
        playlist += said
        lane.cursor += 1
        // why: a lane held back by the floor resumes its pace from now rather than replaying
        // the lag — otherwise it would crowd the next turns to catch up.
        clock = maxOf(clock, lane.nextAt)
        lane.nextAt = clock + step(lane)
        if (lane.spent) lane.open = false
        if (lane.cycles && lane.cursor == lane.members.size) ladder.firstOrNull { !it.open }?.let(::open)
    }
    return playlist
}
