package net.spross.kern.listen

import kotlin.math.ln
import net.spross.kern.model.fnv1a64

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
 * Past it, breadth is the whole point again, leaning softly toward the earlier words
 * ([newWordOrder]).
 */
private const val LISTENING_BASICS_WORDS = 50

/**
 * How far past the basics an unseen word sits when it comes up half as often as the first word
 * past them — the lean of [newWordOrder]'s second half, gentle enough that a word a thousand
 * concepts deep is still heard now and then.
 */
private const val LISTENING_DEPTH_HALVING = 250.0

/**
 * The plain new lane's own within-lane order — shuffled rather than strict catalog order, since
 * catalog order pins the earliest unseen word to the front of every sweep until growth reaches
 * it, which is a queue of one where listening promises a stream.
 *
 * Split in two: the [LISTENING_BASICS_WORDS] earliest concepts first, everything else after,
 * so an empty box still opens on greetings. The basics are hashed and salted with [seed]
 * exactly like [hashedOrder]: they lead as a group, not in seed order.
 * Past them the shuffle is weighted ([arrival]): earlier words tend to come first,
 * and any word may still lead, differently for every [seed].
 */
private fun newWordOrder(seed: Long): Comparator<ListeningCandidate> = compareBy(
    { it.card.seedIndex >= LISTENING_BASICS_WORDS },
    { arrival(it, seed) },
    { it.card.id },
)

/**
 * When a word arrives in the plain new lane. A basic's arrival is its salted hash as a
 * fraction. A deeper word's is an exponential draw from that same fraction, stretched by
 * `1 + depth / LISTENING_DEPTH_HALVING`: sorting such draws is a weighted shuffle whose weight
 * halves at [LISTENING_DEPTH_HALVING] past the basics and keeps falling gently beyond it.
 */
private fun arrival(candidate: ListeningCandidate, seed: Long): Double {
    val hash = fnv1a64("$seed:${fnv1a64(candidate.card.id)}")
    // why: +1 over 2^53 + 1 keeps the fraction strictly inside (0, 1), so its log is finite.
    val unit = ((hash shr 11).toDouble() + 1.0) / (TWO_TO_53 + 1.0)
    val depth = candidate.card.seedIndex - LISTENING_BASICS_WORDS
    if (depth < 0) return unit
    return -ln(unit) * (1.0 + depth / LISTENING_DEPTH_HALVING)
}

private const val TWO_TO_53: Double = 9_007_199_254_740_992.0

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
 * second-guessing the learner's own ask. Plain new words split basics-first, then shuffle
 * within each half, the second leaning toward earlier words ([newWordOrder]): an empty box
 * still opens on greetings, but no single word is pinned to the front forever. Scheduled words are hashed by card id outright, because a
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
