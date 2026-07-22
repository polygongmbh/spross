package net.spross.kern.snapshot

import kotlinx.serialization.Serializable
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.model.CardPhase
import net.spross.kern.store.DayStatsDto
import net.spross.kern.store.StoreJson
import net.spross.kern.store.dayStatsDto

/**
 * Phone-side builder of the iOS widget snapshot (contract §7). The widget extension is
 * decode-only Swift (no catalog in its bundle, tight memory cap), so everything it
 * renders is pre-resolved here on every persist; only `dueCount(now)`,
 * `averageRetrievability(now)`, and the streak walk run at render time, fed by
 * [WidgetUnitDto]/dailyStats.
 */
object WidgetSnapshotBuilder {
    const val SCHEMA_VERSION: Int = 1

    /** ~10 weeks of day keys — enough history for the widget's streak walk. */
    const val DAILY_STATS_TAIL_DAYS: Int = 70

    /** v1 widget timeline depth (24 quarter-hour rotations). */
    const val DEFAULT_EXPOSURE_LIMIT: Int = 24

    fun build(
        state: BoxState,
        nowEpochMillis: Long,
        exposureLimit: Int = DEFAULT_EXPOSURE_LIMIT,
    ): String =
        StoreJson.encodeSorted(WidgetSnapshotDoc.serializer(), doc(state, nowEpochMillis, exposureLimit))

    internal fun doc(state: BoxState, nowEpochMillis: Long, exposureLimit: Int): WidgetSnapshotDoc {
        val entries = BoxEngine.exposureCards(state, nowEpochMillis, exposureLimit).map { card ->
            WidgetEntryDto(
                cardId = card.id,
                text = card.target.text,
                sourceText = decoratedSourceText(card),
                emoji = card.emoji,
                articleTint = articleTint(card),
            )
        }
        val units = Inventory.active(state).mapNotNull { sched ->
            val memory = sched.memory ?: return@mapNotNull null
            val due = sched.due ?: return@mapNotNull null
            WidgetUnitDto(
                cardId = sched.cardId,
                due = due.toEpochMilliseconds(),
                stability = memory.stability,
                lastReview = (sched.log.lastOrNull()?.date ?: sched.addedAt).toEpochMilliseconds(),
                review = sched.phase == CardPhase.Review,
            )
        }
        // why: yyyy-MM-dd keys sort chronologically as strings — the tail is a plain sort.
        val tailKeys = state.dailyStats.keys.sorted().takeLast(DAILY_STATS_TAIL_DAYS)
        return WidgetSnapshotDoc(
            schemaVersion = SCHEMA_VERSION,
            entries = entries,
            units = units,
            dailyStats = tailKeys.associateWith { dayStatsDto(state.dailyStats.getValue(it)) },
        )
    }
}

/** Widget document; all dates are epoch millis for trivial Swift decoding. */
@Serializable
internal data class WidgetSnapshotDoc(
    val schemaVersion: Int,
    /** Pre-resolved exposure rows, deduped by card, most attention-worthy first. */
    val entries: List<WidgetEntryDto>,
    /** Every active unit — render-time dueCount/averageRetrievability inputs. */
    val units: List<WidgetUnitDto>,
    /** Trailing [WidgetSnapshotBuilder.DAILY_STATS_TAIL_DAYS] day keys. */
    val dailyStats: Map<String, DayStatsDto>,
)

/** One exposure row: TARGET-side text; the ♀ marker is baked into [sourceText]. */
@Serializable
internal data class WidgetEntryDto(
    val cardId: String,
    val text: String,
    val sourceText: String,
    val emoji: String? = null,
    val articleTint: String? = null,
)

/**
 * One active unit. `dueCount(now)` = distinct [cardId] with `due <= now` (concept
 * denomination); retrievability averages the FSRS power curve over [review] units
 * with elapsed = now − [lastReview].
 */
@Serializable
internal data class WidgetUnitDto(
    val cardId: String,
    val due: Long,
    val stability: Double,
    val lastReview: Long,
    val review: Boolean,
)
