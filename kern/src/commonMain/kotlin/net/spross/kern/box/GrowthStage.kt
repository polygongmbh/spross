package net.spross.kern.box

import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling

/**
 * Days of stability at which a card counts as MATURED — the third and last bar,
 * a month out from the next sight of the word.
 *
 * Unlike [net.spross.kern.model.BoxConfig.consolidatedStability] this one gates NOTHING:
 * no presentation support, no phrase unlock, no budget. It exists so the ladder has
 * a top Sprosse to report, which is why it is a constant here rather than a config
 * field — there is no product decision to tune behind it.
 */
const val MATURED_STABILITY: Double = 30.0

/**
 * Days of stability at which a Matured card draws as fruit rather than a blossom on the
 * area tree — reporting-only, like [MATURED_STABILITY], and kern's for the same reason
 * [net.spross.kern.model.kindEmoji] is: one platform minting this cutoff is one platform
 * drawing a different tree than the other once both render it.
 */
const val FRUIT_STABILITY: Double = 120.0

/**
 * How far one card has come, as one Sprosse of the box's own ladder.
 *
 * The Sprossen name the RULE, never a picture. A surface is free to draw them as it
 * likes, and free to draw two of them the same — which bars a card has cleared is
 * the engine's answer, what that looks like is not.
 *
 * Ordered as growth runs, so neighboring Sprossen compare. The three off-path Sprossen
 * ([Unscheduled], [Relearning], [Suspended]) say where the card stands now, never
 * how far it once got: a lapsed card reports [Relearning] whatever it had reached.
 */
enum class GrowthStage {
    /** No schedule, and not packed either — a word the box holds and has never opened. */
    Unscheduled,

    /** Packed by the learner, waiting for a round to bring it in ([BoxEngine.enqueue]). */
    Queued,

    /** Scheduled and still walking the learning steps — introduction is the first ANSWER. */
    Learning,

    /** In Review, still under [net.spross.kern.model.BoxConfig.consolidatedStability]. */
    Fresh,

    /** Consolidated: see [Statistics.isConsolidated] — the "has this word landed" bar. */
    Consolidated,

    /** Matured: in Review at or above [MATURED_STABILITY]. */
    Matured,

    /** Lapsed and earning its stability back. */
    Relearning,

    /** Out of rotation — hand-suspended. */
    Suspended,
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
)

/**
 * The Sprosse this schedule stands on. Suspension and a lapse outrank every bar:
 * a suspended card is out of rotation whatever its stability says, and a lapsed
 * one has to earn the bar back before it may claim it again.
 */
internal fun stageOf(state: BoxState, sched: CardScheduling): GrowthStage = when {
    sched.suspended -> GrowthStage.Suspended
    sched.phase == CardPhase.Relearning -> GrowthStage.Relearning
    sched.phase != CardPhase.Review -> GrowthStage.Learning
    (sched.memory?.stability ?: 0.0) >= MATURED_STABILITY -> GrowthStage.Matured
    Statistics.isConsolidated(state, sched) -> GrowthStage.Consolidated
    else -> GrowthStage.Fresh
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
            sched != null -> stageOf(state, sched)
            cardId in queued -> GrowthStage.Queued
            else -> GrowthStage.Unscheduled
        },
        stability = sched?.memory?.stability ?: 0.0,
        touchedToday = sched?.log?.lastOrNull()
            ?.let { dayKey(it.date.toEpochMilliseconds(), tzId) == today } ?: false,
    )
}
