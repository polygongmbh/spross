package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.spross.kern.model.fnv1a64

/** Local calendar date of the instant in the caller's zone. */
internal fun localDate(nowEpochMillis: Long, tzId: String): LocalDate =
    Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(TimeZone.of(tzId)).date

/**
 * Day key = ISO-8601 `yyyy-MM-dd` of the local date — ISO regardless of any device
 * calendar (fixes v1's latent non-Gregorian bug). Keys compare chronologically as strings.
 *
 * Public because it is the key [BoxState.dailyStats] is written under: anything reading
 * that map by day has to agree with the engine on what a day is called, and a platform
 * that formats the key itself has to force a Gregorian calendar to get there.
 */
fun dayKey(nowEpochMillis: Long, tzId: String): String =
    localDate(nowEpochMillis, tzId).toString()

/**
 * The moment tomorrow ends locally — the horizon inside which pulling a card forward
 * costs almost no spacing.
 *
 * Public because it is also the horizon the app previews ("due by tomorrow evening"):
 * pass it to [BoxEngine.dueNow] rather than re-deriving two-days-from-local-midnight
 * with a device calendar.
 */
fun endOfTomorrow(nowEpochMillis: Long, tzId: String): Instant =
    localDate(nowEpochMillis, tzId)
        .plus(2, DateTimeUnit.DAY)
        .atStartOfDayIn(TimeZone.of(tzId))

/**
 * The stretch of the local day an instant falls in — what a line that greets the learner
 * turns on. Sunrise and sunset are not in it: the boundaries are the ones a phone learner
 * keeps, not the sun's, so they hold at every latitude and in every season.
 */
enum class DayPart { Morning, Day, Evening, Night }

fun dayPart(nowEpochMillis: Long, tzId: String): DayPart =
    when (Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(TimeZone.of(tzId)).hour) {
        in 5..10 -> DayPart.Morning
        in 11..17 -> DayPart.Day
        in 18..21 -> DayPart.Evening
        else -> DayPart.Night
    }

/**
 * Which of [count] phrasings a line that renames itself takes right now.
 *
 * Holds through one [dayPart] of one local day, and has moved on by the next time the app
 * is opened: a line re-rolling between renders reads as a glitch, and one that never moves
 * stops being read at all.
 *
 * FNV-1a over the day and the part, never a runtime hash — Swift seeds `hashValue` per
 * process and Kotlin's `hashCode` is no contract either, so the same hour would read
 * differently after a relaunch, or differently on the two phones.
 */
fun partVariant(nowEpochMillis: Long, tzId: String, count: Int): Int {
    var hash = fnv1a64("${dayKey(nowEpochMillis, tzId)}:${dayPart(nowEpochMillis, tzId)}")
    // why: FNV leaves its low bits barely mixed, and the modulo reads exactly those.
    hash = hash xor (hash shr 33)
    return (hash % count.toULong()).toInt()
}
