package net.spross.kern.box

import net.spross.kern.model.CardPhase
import net.spross.kern.model.CardScheduling

/**
 * Days of stability at which a card counts as MATURED — the third and last bar,
 * a month out from the next sight of the word.
 *
 * Unlike [net.spross.kern.model.BoxConfig.consolidatedStability] this one gates NOTHING:
 * no presentation support, no phrase unlock, no budget. It exists so the ladder has
 * a top rung to report, which is why it is a constant here rather than a config
 * field — there is no product decision to tune behind it.
 */
const val MATURED_STABILITY: Double = 30.0

/**
 * How far one card has come, as one rung of the box's own ladder.
 *
 * The rungs name the RULE, never a picture. A surface is free to draw them as it
 * likes, and free to draw two of them the same — which bars a card has cleared is
 * the engine's answer, what that looks like is not.
 *
 * Ordered as growth runs, so neighbouring rungs compare. The three off-path rungs
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

    /** Out of rotation — hand-suspended, or a leech the box suspended itself. */
    Suspended,
}

/**
 * One card's standing: which rung it is on, and the two facts a caller would
 * otherwise re-derive from the schedule to say anything more.
 */
data class CardGrowth(
    val cardId: String,
    val stage: GrowthStage,
    /**
     * Days of stability, 0 for a card with no schedule. Reported raw rather than
     * scaled: the ladder's rungs are coarse by design, and a surface that wants a
     * continuous figure should scale this against
     * [net.spross.kern.model.BoxConfig.maximumIntervalDays] itself.
     */
    val stability: Double,
    /** Whether the card was answered today — the day's growth, where it happened. */
    val touchedToday: Boolean,
)

/**
 * The rung this schedule stands on. Suspension and a lapse outrank every bar:
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
    return Inventory.joinedCards(state).map { card ->
        val sched = state.scheduling[card.id]
        CardGrowth(
            cardId = card.id,
            stage = when {
                sched != null -> stageOf(state, sched)
                card.id in queued -> GrowthStage.Queued
                else -> GrowthStage.Unscheduled
            },
            stability = sched?.memory?.stability ?: 0.0,
            touchedToday = sched?.log?.lastOrNull()
                ?.let { dayKey(it.date.toEpochMilliseconds(), tzId) == today } ?: false,
        )
    }
}
