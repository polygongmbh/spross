package net.spross.kern.box

import kotlin.concurrent.Volatile
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.spross.kern.model.Language
import net.spross.kern.model.fnv1a64
import net.spross.kern.trainer.greetingPart

/** The last zone resolved by name, kept so the next call by the same name is free. */
private class ResolvedZone(val id: String, val zone: TimeZone)

@Volatile
private var lastZone: ResolvedZone? = null

/**
 * The zone named [tzId].
 *
 * Resolving a zone by NAME reads its rules from the platform's zone database — a file
 * read on a device — and the engine asks several times over for a single screenful,
 * while the answer only changes when the LEARNER does. So the last one is kept.
 *
 * This is not a cached derivation: nothing about the box is remembered here, only the
 * lookup of a name that is the same name almost every time. A race between two threads
 * costs one extra resolution and settles on an equal value.
 */
internal fun zoneOf(tzId: String): TimeZone {
    lastZone?.let { if (it.id == tzId) return it.zone }
    val zone = TimeZone.of(tzId)
    lastZone = ResolvedZone(tzId, zone)
    return zone
}

/** Local calendar date of the instant in the caller's zone. */
internal fun localDate(nowEpochMillis: Long, tzId: String): LocalDate =
    Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(zoneOf(tzId)).date

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
        .atStartOfDayIn(zoneOf(tzId))

/** The stretch of the local day an instant falls in — what a greeting turns on. */
enum class DayPart { Morning, Day, Evening, Night }

/**
 * Which stretch of the day the LANGUAGE being greeted in is in — its own seams, read off
 * the words its clock drill teaches (`trainer/ClockDayParts.kt`), never a second table of
 * boundaries here. Languages disagree, and the drill already carries the disagreement:
 * Swahili is at *jioni* by four in the afternoon while German is still at *nachmittags*.
 */
fun dayPart(nowEpochMillis: Long, tzId: String, language: Language?): DayPart =
    greetingPart(
        language,
        Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(zoneOf(tzId)).hour,
    )

/**
 * Which stretch of the day it is for CHROME copy — a fixed, language-neutral schedule,
 * never a taught language's own clock words ([dayPart]). The line calling the learner a
 * night owl is authored in their known language, but it is still just chrome, and chrome
 * keeps the same hours regardless of which language that is: the boundary a Swahili
 * speaker's own clock draws at 7pm ([net.spross.kern.trainer.SwahiliClock]) is right for
 * teaching `usiku`, not for deciding when the app calls someone a night owl.
 */
fun chromePart(nowEpochMillis: Long, tzId: String): DayPart {
    val hour = Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(zoneOf(tzId)).hour
    return when (hour) {
        in 5..10 -> DayPart.Morning
        in 11..16 -> DayPart.Day
        in 17..21 -> DayPart.Evening
        else -> DayPart.Night
    }
}

/**
 * Which of [count] phrasings a line that renames itself takes right now.
 *
 * Holds through one pairing of [chromePart] and [dayPart] of one local day, and has moved
 * on by the next time the app is opened: a line re-rolling between renders reads as a
 * glitch, and one that never moves stops being read at all. Both feed the key because the
 * line mixes chrome's fixed schedule with the target language's own, and either can
 * change part on its own.
 *
 * FNV-1a over the day and the parts, never a runtime hash — Swift seeds `hashValue` per
 * process and Kotlin's `hashCode` is no contract either, so the same hour would read
 * differently after a relaunch, or differently on the two phones.
 */
fun partVariant(nowEpochMillis: Long, tzId: String, targetLanguage: Language?, count: Int): Int {
    val key = "${dayKey(nowEpochMillis, tzId)}:" +
        "${chromePart(nowEpochMillis, tzId)}:" +
        dayPart(nowEpochMillis, tzId, targetLanguage)
    var hash = fnv1a64(key)
    // why: FNV leaves its low bits barely mixed, and the modulo reads exactly those.
    hash = hash xor (hash shr 33)
    return (hash % count.toULong()).toInt()
}
