package net.spross.kern.box

import kotlin.time.Instant
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
    /**
     * Joined cards by id — the join filter for every inventory read. The catalog join
     * plus whatever [ownWords] contributes to it; nothing else may put a card here.
     */
    val cards: Map<String, Card>,
    /** Identifies the join this state was built against; plans carry it for staleness. */
    val joinStamp: JoinStamp,
    /** ONE schedule per card, keyed by card id — join-independent. */
    val scheduling: Map<String, CardScheduling> = emptyMap(),
    /** User priority queue of card ids, front first. */
    val enqueued: List<String> = emptyList(),
    /** dayKey → cards introduced; pruned to trailing 60 days. */
    val newIntroduced: Map<String, Int> = emptyMap(),
    /** dayKey → cards that crossed into CONSOLIDATED; pruned with [newIntroduced]. */
    val consolidatedCrossed: Map<String, Int> = emptyMap(),
    /** dayKey → aggregates; never pruned. */
    val dailyStats: Map<String, DayStats> = emptyMap(),
    /**
     * Words the learner wrote themselves, in the order they wrote them. Unlike
     * [cards] these ARE persisted — they are content nothing else holds, so losing
     * them would lose the word, not merely a derivation of it.
     */
    val ownWords: List<OwnWord> = emptyList(),
    /**
     * Content problems the learner filed, by card id. Persisted and kept across a
     * [BoxEngine.reset] for the same reason [ownWords] is: a report is something they
     * WROTE, not progress the box computed, and clearing what the box knows must never
     * throw away what it holds.
     */
    val reportedIssues: Map<String, ReportedIssue> = emptyMap(),
    /**
     * When the learner last copied or mailed their words and reports out; null until
     * they ever have. What "only what is new" measures against — see [Feedback].
     */
    val lastExportAt: Instant? = null,
)


