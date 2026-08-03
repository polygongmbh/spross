package net.spross.kern.box

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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
