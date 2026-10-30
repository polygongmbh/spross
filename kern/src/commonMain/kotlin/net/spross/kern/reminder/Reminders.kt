package net.spross.kern.reminder

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.localDate
import net.spross.kern.box.zoneOf
import net.spross.kern.store.StoredBoxes

/** When the learner asked to be reminded; [Off] until they turn it on. */
enum class ReminderMode { Off, Usual, Set }

/** One reminder to post: when, and how many words of the box it was planned from stand due by then. */
data class ReminderFire(val atEpochMillis: Long, val dueCount: Int)

/** The reminders to post now, replacing every one posted before; [paused] once ignoring them has stopped them. */
data class ReminderPlan(val fires: List<ReminderFire>, val paused: Boolean)

/**
 * The practice reminders, minted here and posted by the platform as local notifications.
 *
 * - [ReminderMode.Set] fires at each of the learner's chosen times of day.
 * - [ReminderMode.Usual] fires [LEAD_MINUTES] before each time of day the learner habitually starts practicing,
 *   learned from the last [WINDOW_DAYS] days of answers in every language:
 *   a session starts after a [SESSION_GAP_MINUTES] pause, sessions within [CLUSTER_MINUTES] of each other share a time,
 *   and a time that holds sessions on at least [SLOT_SHARE] of the practice days is one reminder a day —
 *   a morning-and-evening learner gets two.
 *   Under [MIN_PRACTICE_DAYS] it is the time the last session started, and with no recent answers the chosen times.
 * - A reminder is dropped once the learner has answered since the one before it that day, or since midnight.
 * - Days without an answer since the last practice day (or since reminders were turned on) thin them out:
 *   from [THIN_AFTER_DAYS] on they come every other day, and from [STOP_AFTER_DAYS] on they stop until the learner answers again.
 *
 * The platform calls [plan] whenever the box is saved and posts [ReminderPlan.fires] in place of what it posted before,
 * so a reminder already answered never goes out.
 */
object Reminders {
    const val HORIZON_DAYS: Int = 7
    const val WINDOW_DAYS: Int = 28
    const val MIN_PRACTICE_DAYS: Int = 5
    const val SESSION_GAP_MINUTES: Int = 120
    const val CLUSTER_MINUTES: Int = 90
    const val SLOT_SHARE: Double = 0.4
    const val LEAD_MINUTES: Int = 15
    const val THIN_AFTER_DAYS: Int = 3
    const val STOP_AFTER_DAYS: Int = 7

    /** [setMinutes]: the chosen times as minutes after local midnight. [state]'s due words fill each fire's count. */
    fun plan(
        mode: ReminderMode,
        setMinutes: List<Int>,
        enabledAtEpochMillis: Long,
        state: BoxState,
        boxes: StoredBoxes,
        nowEpochMillis: Long,
        tzId: String,
    ): ReminderPlan {
        val schedules = boxes.boxes.filterKeys { it != state.joinStamp.target }.values.map { it.scheduling } + state.scheduling
        val answers = schedules.flatMap { byCard -> byCard.values.flatMap { s -> s.log.map { it.date } } }
        val (times, paused) = fireTimes(mode, setMinutes, enabledAtEpochMillis, answers, nowEpochMillis, tzId)
        return ReminderPlan(
            fires = times.map { ReminderFire(it.toEpochMilliseconds(), BoxEngine.dueCount(state, it.toEpochMilliseconds())) },
            paused = paused,
        )
    }

    internal fun fireTimes(
        mode: ReminderMode,
        setMinutes: List<Int>,
        enabledAtEpochMillis: Long,
        answers: List<Instant>,
        nowEpochMillis: Long,
        tzId: String,
    ): Pair<List<Instant>, Boolean> {
        if (mode == ReminderMode.Off) return emptyList<Instant>() to false
        val zone = zoneOf(tzId)
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        val today = localDate(nowEpochMillis, tzId)
        val minutes = when (mode) {
            ReminderMode.Set -> setMinutes
            else -> usualMinutes(answers, nowEpochMillis, tzId) ?: setMinutes
        }.distinct().sorted()
        val lastAnswer = answers.maxOrNull()
        // why: the day before turning reminders on counts as practiced, so a long pause before never reads as ignored reminders.
        val lastPracticed = maxOf(
            lastAnswer?.toLocalDateTime(zone)?.date ?: LocalDate.fromEpochDays(0),
            localDate(enabledAtEpochMillis, tzId).plus(-1, DateTimeUnit.DAY),
        )
        val fires = mutableListOf<Instant>()
        for (offset in 0 until HORIZON_DAYS) {
            val day = today.plus(offset, DateTimeUnit.DAY)
            val missed = lastPracticed.daysUntil(day) - 1
            if (missed >= STOP_AFTER_DAYS || (missed >= THIN_AFTER_DAYS && missed % 2 == 1)) continue
            var since = day.atStartOfDayIn(zone)
            for (minute in minutes) {
                val at = LocalDateTime(day, LocalTime.fromSecondOfDay(minute.coerceIn(0, 1439) * 60)).toInstant(zone)
                val answered = lastAnswer != null && lastAnswer >= since
                if (at > now && !answered) fires += at
                since = at
            }
        }
        val missedToday = lastPracticed.daysUntil(today) - 1
        return fires to (missedToday >= STOP_AFTER_DAYS)
    }

    /** The times of day, as minutes after midnight, the learner habitually starts practicing; null with no answers. */
    internal fun usualMinutes(answers: List<Instant>, nowEpochMillis: Long, tzId: String): List<Int>? {
        val zone = zoneOf(tzId)
        val from = localDate(nowEpochMillis, tzId).plus(-WINDOW_DAYS, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val recent = answers.filter { it >= from }.sorted()
        if (recent.isEmpty()) return null
        val starts = recent.filterIndexed { i, at ->
            i == 0 || (at - recent[i - 1]).inWholeMinutes >= SESSION_GAP_MINUTES
        }.map { it.toLocalDateTime(zone) }
        val practiceDays = recent.map { it.toLocalDateTime(zone).date }.distinct().size
        if (practiceDays < MIN_PRACTICE_DAYS) return listOf(lead(minuteOf(starts.last())))
        val clusters = mutableListOf<MutableList<LocalDateTime>>()
        for (start in starts.sortedBy { minuteOf(it) }) {
            val last = clusters.lastOrNull()
            if (last != null && minuteOf(start) - minuteOf(last.last()) <= CLUSTER_MINUTES) last += start else clusters += mutableListOf(start)
        }
        val habitual = clusters.filter { c -> c.map { it.date }.distinct().size >= SLOT_SHARE * practiceDays }
            .ifEmpty { listOf(clusters.maxBy { c -> c.map { it.date }.distinct().size }) }
        return habitual.map { c -> lead(minuteOf(c[c.size / 2])) }
    }

    private fun minuteOf(at: LocalDateTime): Int = at.hour * 60 + at.minute

    private fun lead(minute: Int): Int = (minute - LEAD_MINUTES).coerceAtLeast(0)
}
