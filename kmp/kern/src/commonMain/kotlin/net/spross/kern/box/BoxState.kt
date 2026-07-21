package net.spross.kern.box

import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.DayStats
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.UnitScheduling

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
    /** Raw unit schedules keyed by encoded unit key — join-independent. */
    val scheduling: Map<String, UnitScheduling> = emptyMap(),
    /** User priority queue of CONCEPT ids, front first. */
    val enqueued: List<String> = emptyList(),
    /** dayKey → concepts introduced (produce intros); pruned to trailing 60 days. */
    val newIntroduced: Map<String, Int> = emptyMap(),
    /** dayKey → aggregates; never pruned. */
    val dailyStats: Map<String, DayStats> = emptyMap(),
)

enum class AnswerStatus {
    /** The answer was recorded as an FSRS review (or an introduction). */
    Applied,
    /** Unknown or non-joining unit key — a defined no-op the UI skips past. */
    StaleUnit,
    /** Introduction refused: the learning pool is full (defensive re-check). */
    DroppedPoolFull,
}

/** Result of [BoxEngine.answer]: the (possibly unchanged) state plus what happened. */
data class AnswerOutcome(
    val state: BoxState,
    val status: AnswerStatus,
)
