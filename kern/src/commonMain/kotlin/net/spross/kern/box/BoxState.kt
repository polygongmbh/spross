package net.spross.kern.box

import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.CardScheduling
import net.spross.kern.model.DayStats
import net.spross.kern.model.JoinStamp

/**
 * In-memory box aggregate for one TARGET language under the current (source, target) join.
 * [cards] is derived from the catalog join and never persisted; the rest persists.
 * Schedules and enqueued entries whose card does not join the current profile stay in
 * their collections untouched (inert) — they revive when the user switches back.
 */
data class BoxState(
    val config: BoxConfig,
    /** Joined cards by id — the join filter for every inventory read. */
    val cards: Map<String, Card>,
    /** Identifies the join this state was built against; plans carry it for staleness. */
    val joinStamp: JoinStamp,
    /** ONE schedule per card, keyed by card id — join-independent. */
    val scheduling: Map<String, CardScheduling> = emptyMap(),
    /** User priority queue of card ids, front first. */
    val enqueued: List<String> = emptyList(),
    /** dayKey → cards introduced; pruned to trailing 60 days. */
    val newIntroduced: Map<String, Int> = emptyMap(),
    /** dayKey → cards that crossed into settled; pruned with [newIntroduced]. */
    val settledCrossed: Map<String, Int> = emptyMap(),
    /** dayKey → aggregates; never pruned. */
    val dailyStats: Map<String, DayStats> = emptyMap(),
)

enum class AnswerStatus {
    /** The answer was recorded as an FSRS review (or an introduction). */
    Applied,
    /** Unknown or non-joining card id — a defined no-op the UI skips past. */
    StaleCard,
    /**
     * Introduction refused: the card is not introducible under the current state
     * (locked phrase) — defensive re-check; plans outlive phase changes.
     */
    DroppedIneligible,
}

/** Result of [BoxEngine.answer]: the (possibly unchanged) state plus what happened. */
data class AnswerOutcome(
    val state: BoxState,
    val status: AnswerStatus,
)
