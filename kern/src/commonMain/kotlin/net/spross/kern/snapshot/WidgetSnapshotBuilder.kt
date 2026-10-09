package net.spross.kern.snapshot

import kotlinx.serialization.Serializable
import net.spross.kern.box.BoxState
import net.spross.kern.box.Exposure
import net.spross.kern.box.Inventory
import net.spross.kern.box.Statistics
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.answerDays
import net.spross.kern.box.mergeAnswerDays
import net.spross.kern.design.ActivityScale
import net.spross.kern.model.Card
import net.spross.kern.model.Gender
import net.spross.kern.model.article
import net.spross.kern.store.StoreJson
import net.spross.kern.trainer.letters

/**
 * Phone-side builder of the home-screen widget snapshot, and the read model
 * ([decode]) a widget draws it with. A widget surface never runs the join (no catalog
 * in its bundle, tight memory cap), so everything it renders is pre-resolved here on
 * every persist, the streak and the activity strip included for every day it will be rendered on
 * ([WidgetSnapshotDoc.streakByDay], [WidgetSnapshotDoc.activityByDay]) — only `dueCount(now)`,
 * which genuinely moves within a day, runs at render time. Who decodes it how: `kern/docs/snapshots.md`.
 */
object WidgetSnapshotBuilder {
    const val SCHEMA_VERSION: Int = 11

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
     * [otherLanguagesAnswerDays]: [answerDays] from every OTHER target-language box —
     * the streak and the strip are resolved from the merged days,
     * so merging cross-language activity in here is the whole fix; no widget target
     * needs a change.
     */
    fun build(
        state: BoxState,
        nowEpochMillis: Long,
        tzId: String,
        exposureLimit: Int = DEFAULT_EXPOSURE_LIMIT,
        otherLanguagesAnswerDays: Map<String, Int> = emptyMap(),
    ): String =
        StoreJson.encodeSorted(
            WidgetSnapshotDoc.serializer(),
            doc(state, nowEpochMillis, tzId, exposureLimit, otherLanguagesAnswerDays),
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
        tzId: String,
        exposureLimit: Int,
        otherLanguagesAnswerDays: Map<String, Int> = emptyMap(),
    ): WidgetSnapshotDoc {
        val entries = Exposure.exposureCards(state, exposureLimit, ::fitsOnWidget).map { card ->
            WidgetEntryDto(
                cardId = card.id,
                text = card.target.text,
                sourceText = card.source.text,
                emoji = card.emoji,
                article = card.target.article,
                gender = wireGender(card),
            )
        }
        val active = Inventory.active(state)
        val cards = active.mapNotNull { sched ->
            val due = sched.due ?: return@mapNotNull null
            WidgetCardDto(cardId = sched.cardId, due = due.toEpochMilliseconds())
        }
        val combinedDailyStats =
            mergeAnswerDays(listOf(otherLanguagesAnswerDays, answerDays(state.scheduling, tzId, state.drillDays)))
        return WidgetSnapshotDoc(
            schemaVersion = SCHEMA_VERSION,
            chromeLanguage = chromeLanguage(state),
            entries = entries,
            cards = cards,
            allSettledCount = active.count { Statistics.hasSettled(it) },
            activityByDay = activityTimeline(combinedDailyStats, nowEpochMillis, tzId),
            activityHeight = ActivityScale.widget.maxHeight,
            streakByDay = streakTimeline(combinedDailyStats, nowEpochMillis, tzId),
        )
    }

    /**
     * Whether both sides of [card] clear [MAX_TEXT_CHARS] as rendered — the
     * source is measured with its ♀ marker, since that is what the row shows.
     */
    private fun fitsOnWidget(card: Card): Boolean =
        card.target.text.letters().size <= MAX_TEXT_CHARS &&
            card.source.text.letters().size <= MAX_TEXT_CHARS
}

/**
 * A decoded snapshot as a widget reads it: the pre-resolved rows, plus the handful of
 * answers that move with the clock. Every derivation here delegates to the engine's own
 * walk, so a widget can never drift from what the app's statistics say.
 */
class WidgetSnapshotView internal constructor(private val doc: WidgetSnapshotDoc) {

    /** Pre-resolved exposure rows, most attention-worthy first. */
    val entries: List<WidgetExposure> = doc.entries.map {
        WidgetExposure(it.cardId, it.text, it.sourceText, it.emoji, it.article, genderOf(it.gender))
    }

    /** The rows a tile of [count] cells shows at [nowEpochMillis], head first ([WidgetRotation]). */
    fun window(nowEpochMillis: Long, count: Int, stepMillis: Long): List<WidgetExposure> =
        WidgetRotation.window(entries.size, nowEpochMillis, count, stepMillis).map { entries[it] }

    /** Active cards that have settled — resolved phone-side, it does not move with the clock. */
    val allSettledCount: Int get() = doc.allSettledCount

    /** Active cards due at [nowEpochMillis]. */
    fun dueCount(nowEpochMillis: Long): Int = doc.cards.count { it.due <= nowEpochMillis }

    fun streak(nowEpochMillis: Long, tzId: String): Int =
        onRenderDay(doc.streakByDay, nowEpochMillis, tzId).streak

    fun streakHealth(nowEpochMillis: Long, tzId: String): StreakHealth =
        onRenderDay(doc.streakByDay, nowEpochMillis, tzId).health

    /** The header strip's bars on the local day of [nowEpochMillis], oldest first, today last. */
    fun activityBars(nowEpochMillis: Long, tzId: String): List<WidgetBar> =
        onRenderDay(doc.activityByDay, nowEpochMillis, tzId)
}

/** One exposure row: TARGET-side [text]; the ♀ marker is baked into [sourceText]. */
data class WidgetExposure(
    val cardId: String,
    val text: String,
    val sourceText: String,
    val emoji: String? = null,
    /** The article word shown in front of [text]. */
    val article: String? = null,
    /** The gender [article] marks, which is what tints the row; null where the box names none. */
    val gender: Gender? = null,
)

/** Widget document; all dates are epoch millis for trivial Swift decoding. */
@Serializable
internal data class WidgetSnapshotDoc(
    val schemaVersion: Int,
    /** The language the widget's own chrome is written in (`chromeLanguage`). */
    val chromeLanguage: String,
    /** Pre-resolved exposure rows, most attention-worthy first. */
    val entries: List<WidgetEntryDto>,
    /** Every active card's due date — the render-time dueCount input. */
    val cards: List<WidgetCardDto>,
    /** Active cards that have settled; time-independent, so it is resolved here. */
    val allSettledCount: Int,
    /** The activity strip's bars for each day a widget may render on ([activityTimeline]), read by [onRenderDay]. */
    val activityByDay: Map<String, List<WidgetBar>>,
    /** The height the strip's row reserves, its tallest bar's ([ActivityScale.widget]). */
    val activityHeight: Double,
    /**
     * The streak for each day a widget may render on ([streakTimeline]), read by [onRenderDay] —
     * a widget extension with no Kotlin cannot ask [Statistics] later, so it looks its day up here.
     */
    val streakByDay: Map<String, WidgetStreakDto>,
)

/** One exposure row: TARGET-side text; the ♀ marker is baked into [sourceText]. */
@Serializable
internal data class WidgetEntryDto(
    val cardId: String,
    val text: String,
    val sourceText: String,
    val emoji: String? = null,
    val article: String? = null,
    /** `masculine`/`feminine`/`neuter` ([wireGender]); the tint reads this, never [article]. */
    val gender: String? = null,
)

/** One active card schedule: `dueCount(now)` = cards with `due <= now`. */
@Serializable
internal data class WidgetCardDto(
    val cardId: String,
    val due: Long,
)
