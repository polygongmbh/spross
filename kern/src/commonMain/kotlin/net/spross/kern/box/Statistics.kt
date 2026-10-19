package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import net.spross.kern.model.CardKind
import net.spross.kern.model.CardScheduling

/** Aggregates for progress UI. All counts are in cards. */
data class BoxStatistics(
    /** Active (scheduled, non-suspended) cards per stage. */
    val stages: StageCounts,
    /** Active cards due now. */
    val dueCount: Int,
    /** Days with reviews > 0; a missed day is bridged, two in a row end the run. */
    val streak: Int,
    /** What today still owes the run — see [streakHealth]. */
    val streakHealth: StreakHealth,
    val areas: List<AreaStatistics>,
) {
    val activeCount: Int get() = stages.active
    val allSettledCount: Int get() = stages.allSettled
    val allGrowingCount: Int get() = stages.allGrowing
}

data class AreaStatistics(
    val name: String,
    /** Cards in the area (any status). */
    val total: Int,
    /** Active cards in the area per stage. */
    val stages: StageCounts,
    /** Cards queued but not yet introduced — the progress bar's clay segment. */
    val queued: Int = 0,
    /** Component phrases still waiting for their components to stabilize. */
    val phrasesLocked: Int = 0,
) {
    val active: Int get() = stages.active
    val allSettled: Int get() = stages.allSettled
    val allGrowing: Int get() = stages.allGrowing

    /** What the bar is measured against — never below the introduced count. */
    val progressTotal: Int get() = maxOf(total, active, 1)

    /** Every active card has settled, and there is at least one — turns the area-complete mark jade. */
    val fullySettled: Boolean get() = active > 0 && allGrowing == 0
}

internal object Statistics {

    fun statistics(
        state: BoxState,
        nowEpochMillis: Long,
        tzId: String,
        otherLanguagesAnswerDays: Map<String, Int> = emptyMap(),
    ): BoxStatistics {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val active = Inventory.active(state)
        // why: the streak is a box-wide commitment, not a per-target-language one —
        // see [mergeAnswerDays] — everything else here stays scoped to THIS join.
        val combinedDailyStats =
            mergeAnswerDays(listOf(otherLanguagesAnswerDays, answerDays(state.scheduling, tzId, state.drillDays)))
        return BoxStatistics(
            stages = StageCounts.of(active),
            dueCount = active.count { it.due != null && it.due <= now },
            streak = streak(combinedDailyStats, nowEpochMillis, tzId),
            streakHealth = streakHealth(combinedDailyStats, nowEpochMillis, tzId),
            areas = areaStatistics(state, active),
        )
    }

    /**
     * Stability at or above [SETTLED_STABILITY], whatever the phase:
     * a lapse costs the bar only where FSRS's post-lapse stability falls under it.
     */
    fun hasSettled(sched: CardScheduling): Boolean =
        (sched.memory?.stability ?: 0.0) >= SETTLED_STABILITY

    /**
     * Stability at or above [GROWING_STABILITY], whatever the phase —
     * one forgetting is normal, and the support is for words that are not properly down.
     * Gates phrase unlock, the drill pools and the in-session support (emoji cue, sound-only prompt).
     */
    fun hasArrived(sched: CardScheduling): Boolean =
        (sched.memory?.stability ?: 0.0) >= GROWING_STABILITY

    /**
     * Walk back from today: a missed day is bridged, two in a row end the run. Forgiveness
     * is a property of the neighborhood, not a budget — showing up restores it, so a
     * second miss weeks later never takes back the days built before the first. A bridged
     * day does not increment the count: the streak stalls for a day instead of dying.
     *
     * Today without reviews is not a miss at all (the day isn't over) — it neither breaks
     * the run nor pairs with an empty yesterday.
     */
    fun streak(answerDays: Map<String, Int>, nowEpochMillis: Long, tzId: String): Int =
        streakRun(answerDays, localDate(nowEpochMillis, tzId)).count { it.value }

    /**
     * The days the current run covers, newest first: date → earned (false = bridged).
     * The one walk both [streak] and [streakWindow] read, so the count and the
     * days shown as covered are the same answer told twice.
     *
     * Bridges past the oldest earned day drop out: forgiveness spans a run, it does
     * not start one.
     */
    fun streakRun(answerDays: Map<String, Int>, today: LocalDate): Map<LocalDate, Boolean> {
        val walked = mutableListOf<Pair<LocalDate, Boolean>>()
        var previousWasMiss = false
        var day = today
        var isToday = true
        while (true) {
            val reviews = answerDays[day.toString()] ?: 0
            if (reviews > 0) {
                walked += day to true
                previousWasMiss = false
            } else if (!isToday) {
                if (previousWasMiss) break
                previousWasMiss = true
                walked += day to false
            }
            isToday = false
            day = day.minus(1, DateTimeUnit.DAY)
        }
        val oldestEarned = walked.indexOfLast { it.second }
        if (oldestEarned < 0) return emptyMap()
        return walked.take(oldestEarned + 1).toMap()
    }

    private fun areaStatistics(state: BoxState, active: List<CardScheduling>): List<AreaStatistics> {
        val activeByArea = active.groupBy { state.cards[it.cardId]?.area }
        // why: [BoxBrowser.shelfCounts] already walks the queue per area for the queue
        // controls — the bar's clay segment reads the same number rather than a second walk.
        val shelfCounts = BoxBrowser.shelfCounts(state)
        return state.cards.values.groupBy { it.area }.entries
            .sortedBy { it.key }
            .map { (area, cards) ->
                var locked = 0
                for (card in cards) {
                    if (card.kind == CardKind.Phrase) {
                        val open = state.scheduling[card.id] != null || card.components.isEmpty() ||
                            Growth.isPhraseUnlocked(state, card)
                        if (!open) locked += 1
                    }
                }
                AreaStatistics(
                    name = area, total = cards.size,
                    stages = StageCounts.of(activeByArea[area].orEmpty()),
                    queued = shelfCounts[area]?.queued ?: 0,
                    phrasesLocked = locked,
                )
            }
    }
}
