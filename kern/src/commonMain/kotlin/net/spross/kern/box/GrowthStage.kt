package net.spross.kern.box

import kotlin.math.max
import kotlin.math.roundToInt
import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling

/**
 * How far one card has come, as one Sprosse of the box's own ladder.
 *
 * The Sprossen name the RULE, never a picture. A surface is free to draw them as it
 * likes, and free to draw two of them the same — which bars a card has cleared is
 * the engine's answer, what that looks like is not.
 *
 * Ordered as growth runs, so neighboring Sprossen compare. The three off-path Sprossen
 * ([Unscheduled], [Lapsed], [Suspended]) say where the card stands now, never
 * how far it once got.
 */
enum class GrowthStage {
    /** No schedule, and not packed either — a word the box holds and has never opened. */
    Unscheduled,

    /** Packed by the learner, waiting for a round to bring it in ([BoxEngine.enqueue]). */
    Queued,

    /**
     * Met and still under [GROWING_STABILITY], and not in the
     * relearning steps. Introduction is the first ANSWER.
     */
    Fresh,

    /** Arrived and short of [SETTLED_STABILITY]: see [Statistics.hasArrived] (gate (a)). */
    Growing,

    /** At or above [SETTLED_STABILITY]: see [Statistics.hasSettled]. */
    Settled,

    /**
     * In the relearning steps and under the growing bar — a word that slipped and did not
     * keep enough stability to stay arrived. A lapse that kept it reads by its stability.
     */
    Lapsed,

    /** Out of rotation — hand-suspended. */
    Suspended,
}

/**
 * The Sprosse of an active card — scheduled and in rotation —
 * so a type that only ever holds one cannot be handed [GrowthStage]'s other three.
 */
enum class ActiveStage(val growth: GrowthStage) {
    Fresh(GrowthStage.Fresh),
    Growing(GrowthStage.Growing),
    Settled(GrowthStage.Settled),
    Lapsed(GrowthStage.Lapsed),
}

/**
 * One card's standing: which Sprosse it is on, and the two facts a caller would
 * otherwise re-derive from the schedule to say anything more.
 */
data class CardGrowth(
    val cardId: String,
    val stage: GrowthStage,
    /**
     * Days of stability, 0 for a card with no schedule. Reported raw rather than
     * scaled: the ladder's Sprossen are coarse by design, and a surface that wants a
     * continuous figure should scale this against
     * [net.spross.kern.model.BoxConfig.maximumIntervalDays] itself.
     */
    val stability: Double,
    /** Whether the card was answered today — the day's growth, where it happened. */
    val touchedToday: Boolean,
) {
    /**
     * Whole days the word keeps, at least one — or null with no stability to speak of,
     * where "keeps 0 days" would be a fact about the engine rather than about the word.
     */
    val lastsDays: Int? get() = if (stability > 0.0) max(1, stability.roundToInt()) else null
}

/**
 * The Sprosse this schedule stands on: suspension first, then the stability bars,
 * and only under the growing bar does the FSRS phase say whether the word is new or slipped.
 */
internal fun stageOf(sched: CardScheduling): GrowthStage =
    if (sched.suspended) GrowthStage.Suspended else activeStageOf(sched).growth

/** [stageOf] for a schedule in rotation, whatever its suspension says. */
internal fun activeStageOf(sched: CardScheduling): ActiveStage = when {
    Statistics.hasSettled(sched) -> ActiveStage.Settled
    Statistics.hasArrived(sched) -> ActiveStage.Growing
    sched.phase == CardPhase.Relearning -> ActiveStage.Lapsed
    else -> ActiveStage.Fresh
}

/**
 * Every joined card's standing, in seed order — one pass, so a surface drawing the
 * whole box asks once instead of per card.
 *
 * Read through [Inventory.joinedCards] like every other inventory query: a card the
 * current join does not carry is not in the box and has no standing in it.
 */
internal fun boxGrowth(state: BoxState, nowEpochMillis: Long, tzId: String): List<CardGrowth> {
    val queued = state.enqueued.toSet()
    val today = dayKey(nowEpochMillis, tzId)
    return Inventory.joinedCards(state).map { growthOf(state, it.id, queued, today, tzId) }
}

/** [boxGrowth] for one area's cards alone — a summary drawing one tree walks only that tree. */
internal fun areaGrowth(state: BoxState, area: String, nowEpochMillis: Long, tzId: String): List<CardGrowth> {
    val queued = state.enqueued.toSet()
    val today = dayKey(nowEpochMillis, tzId)
    return state.cards.values.filter { it.area == area }
        .map { growthOf(state, it.id, queued, today, tzId) }
}

/**
 * ONE card's standing, or null where the join does not carry it — the same ruling
 * [boxGrowth] reports for the whole box, asked by name.
 *
 * For a surface that has a single word in front of it (a row's long press): walking the
 * whole box for one entry is the alternative, and a surface tempted to skip that walk is
 * a surface about to re-derive the Sprosse from the schedule itself.
 */
internal fun cardGrowthOf(
    state: BoxState,
    cardId: String,
    nowEpochMillis: Long,
    tzId: String,
): CardGrowth? {
    if (cardId !in state.cards) return null
    return growthOf(state, cardId, state.enqueued.toSet(), dayKey(nowEpochMillis, tzId), tzId)
}

private fun growthOf(
    state: BoxState,
    cardId: String,
    queued: Set<String>,
    today: String,
    tzId: String,
): CardGrowth {
    val sched = state.scheduling[cardId]
    return CardGrowth(
        cardId = cardId,
        stage = when {
            sched != null -> stageOf(sched)
            cardId in queued -> GrowthStage.Queued
            else -> GrowthStage.Unscheduled
        },
        stability = sched?.memory?.stability ?: 0.0,
        touchedToday = sched?.log?.lastOrNull()
            ?.let { dayKey(it.date.toEpochMilliseconds(), tzId) == today } ?: false,
    )
}
