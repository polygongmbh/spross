package net.spross.kern.snapshot

import kotlinx.serialization.Serializable
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.box.Statistics
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.mergeDailyStats
import net.spross.kern.box.streakHealth
import net.spross.kern.box.streakWindow
import net.spross.kern.model.Card
import net.spross.kern.model.DayStats
import net.spross.kern.store.DayStatsDto
import net.spross.kern.store.StoreJson
import net.spross.kern.store.dayStatsDto
import net.spross.kern.store.toDomain

/**
 * Phone-side builder of the home-screen widget snapshot, and the read model
 * ([decode]) a widget draws it with. A widget surface never runs the join (no catalog
 * in its bundle, tight memory cap), so everything it renders is pre-resolved here on
 * every persist; only `dueCount(now)` and the streak walk run at render time, fed by
 * [WidgetCardDto]/dailyStats. Who decodes it how: `kern/docs/snapshots.md`.
 */
object WidgetSnapshotBuilder {
    const val SCHEMA_VERSION: Int = 2

    /** ~10 weeks of day keys — enough history for the widget's streak walk. */
    const val DAILY_STATS_TAIL_DAYS: Int = 70

    /** v1 widget timeline depth (24 quarter-hour rotations). */
    const val DEFAULT_EXPOSURE_LIMIT: Int = 24

    /**
     * Longest text a widget row can hold. A row gives each side a share of one
     * tile-width line, so a longer phrase only arrives shrunken to the point of
     * being unreadable at a glance — which is all a widget is for. It is taught
     * on the phone, where it has a card to itself.
     *
     * Tighter than the watch's [WatchSnapshotBuilder.MAX_TEXT_CHARS]: the watch
     * gives a tile its own line, the widget puts word and meaning on one.
     */
    const val MAX_TEXT_CHARS: Int = 20

    /**
     * [otherLanguagesDailyStats]: `dailyStats` from every OTHER target-language box —
     * every render-time streak walk reads whatever [WidgetSnapshotDoc.dailyStats] carries,
     * so merging cross-language activity in here is the whole fix; no widget target
     * needs a change.
     */
    fun build(
        state: BoxState,
        nowEpochMillis: Long,
        exposureLimit: Int = DEFAULT_EXPOSURE_LIMIT,
        otherLanguagesDailyStats: List<Map<String, DayStats>> = emptyList(),
    ): String =
        StoreJson.encodeSorted(
            WidgetSnapshotDoc.serializer(),
            doc(state, nowEpochMillis, exposureLimit, otherLanguagesDailyStats),
        )

    /**
     * The read side of [build]: null for JSON this build cannot make sense of —
     * unparseable, or a [SCHEMA_VERSION] it does not know. A widget that gets null
     * draws its no-snapshot face; it never renders half a schema.
     */
    fun decode(json: String): WidgetSnapshotView? {
        val doc = try {
            StoreJson.json.decodeFromString(WidgetSnapshotDoc.serializer(), json)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return if (doc.schemaVersion == SCHEMA_VERSION) WidgetSnapshotView(doc) else null
    }

    internal fun doc(
        state: BoxState,
        nowEpochMillis: Long,
        exposureLimit: Int,
        otherLanguagesDailyStats: List<Map<String, DayStats>> = emptyList(),
    ): WidgetSnapshotDoc {
        val entries = BoxEngine.exposureCards(state, nowEpochMillis, exposureLimit, ::fitsOnWidget).map { card ->
            WidgetEntryDto(
                cardId = card.id,
                text = card.target.text,
                sourceText = decoratedSourceText(card),
                emoji = card.emoji,
                articleTint = articleTint(card),
            )
        }
        val active = Inventory.active(state)
        val cards = active.mapNotNull { sched ->
            val due = sched.due ?: return@mapNotNull null
            WidgetCardDto(cardId = sched.cardId, due = due.toEpochMilliseconds())
        }
        val combinedDailyStats = mergeDailyStats(otherLanguagesDailyStats + state.dailyStats)
        // why: yyyy-MM-dd keys sort chronologically as strings — the tail is a plain sort.
        val tailKeys = combinedDailyStats.keys.sorted().takeLast(DAILY_STATS_TAIL_DAYS)
        return WidgetSnapshotDoc(
            schemaVersion = SCHEMA_VERSION,
            entries = entries,
            cards = cards,
            consolidatedCount = active.count { Statistics.isConsolidated(state, it) },
            dailyStats = tailKeys.associateWith { dayStatsDto(combinedDailyStats.getValue(it)) },
        )
    }

    /**
     * Whether both sides of [card] clear [MAX_TEXT_CHARS] as rendered — the
     * source is measured with its ♀ marker, since that is what the row shows.
     */
    private fun fitsOnWidget(card: Card): Boolean =
        card.target.text.length <= MAX_TEXT_CHARS &&
            decoratedSourceText(card).length <= MAX_TEXT_CHARS
}

/**
 * A decoded snapshot as a widget reads it: the pre-resolved rows, plus the handful of
 * answers that move with the clock. Every derivation here delegates to the engine's own
 * walk, so a widget can never drift from what the app's statistics say.
 */
class WidgetSnapshotView internal constructor(private val doc: WidgetSnapshotDoc) {

    /** Pre-resolved exposure rows, most attention-worthy first. */
    val entries: List<WidgetExposure> = doc.entries.map {
        WidgetExposure(it.cardId, it.text, it.sourceText, it.emoji, it.articleTint)
    }

    /** Active cards that have consolidated — resolved phone-side, it does not move with the clock. */
    val consolidatedCount: Int get() = doc.consolidatedCount

    private val dailyStats: Map<String, DayStats> = doc.dailyStats.mapValues { it.value.toDomain() }

    /** Active cards due at [nowEpochMillis]. */
    fun dueCount(nowEpochMillis: Long): Int = doc.cards.count { it.due <= nowEpochMillis }

    fun streak(nowEpochMillis: Long, tzId: String): Int =
        Statistics.streak(dailyStats, nowEpochMillis, tzId)

    fun streakHealth(nowEpochMillis: Long, tzId: String): StreakHealth =
        streakHealth(dailyStats, nowEpochMillis, tzId)

    /** The trailing [days] local days, oldest first — the header strip's input. */
    fun activityWindow(days: Int, nowEpochMillis: Long, tzId: String): List<ActivityDay> =
        streakWindow(dailyStats, days, nowEpochMillis, tzId)
}

/** One exposure row: TARGET-side [text]; the ♀ marker is baked into [sourceText]. */
data class WidgetExposure(
    val cardId: String,
    val text: String,
    val sourceText: String,
    val emoji: String? = null,
    /** The article word, which is also what tints the row. */
    val articleTint: String? = null,
)

/** Widget document; all dates are epoch millis for trivial Swift decoding. */
@Serializable
internal data class WidgetSnapshotDoc(
    val schemaVersion: Int,
    /** Pre-resolved exposure rows, most attention-worthy first. */
    val entries: List<WidgetEntryDto>,
    /** Every active card's due date — the render-time dueCount input. */
    val cards: List<WidgetCardDto>,
    /** Active cards that have consolidated; time-independent, so it is resolved here. */
    val consolidatedCount: Int,
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

/** One active card schedule: `dueCount(now)` = cards with `due <= now`. */
@Serializable
internal data class WidgetCardDto(
    val cardId: String,
    val due: Long,
)
