package net.spross.kern.box

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import net.spross.kern.model.CardScheduling

/**
 * Answers per local day, counted off the review logs — what the streak, the activity strip
 * and "done today" read. The logs already carry every answer, so the box keeps no tally
 * beside them that could disagree with them.
 *
 * Days are cut in the CALLER's timezone, the one the learner is standing in now; an answer
 * given elsewhere can therefore move a day, which costs a traveller nothing that matters.
 * Suspended and unjoined schedules count as well — the answer really happened, and a source
 * switch must not un-happen a day.
 */
fun answerDays(scheduling: Map<String, CardScheduling>, tzId: String): Map<String, Int> {
    val days = mutableMapOf<String, Int>()
    for (sched in scheduling.values) {
        for (entry in sched.log) {
            val day = dayKey(entry.date.toEpochMilliseconds(), tzId)
            days[day] = (days[day] ?: 0) + 1
        }
    }
    return days
}

/**
 * One day's answers across every target language. Growing is one commitment, not one per
 * language the learner happens to study, so a day earns the streak whichever it was spent on.
 */
fun mergeAnswerDays(answerDaysByLanguage: List<Map<String, Int>>): Map<String, Int> {
    if (answerDaysByLanguage.size <= 1) return answerDaysByLanguage.firstOrNull() ?: emptyMap()
    val merged = mutableMapOf<String, Int>()
    for (byDay in answerDaysByLanguage) {
        for ((day, count) in byDay) merged[day] = (merged[day] ?: 0) + count
    }
    return merged
}

/**
 * Answers logged on the local day [nowEpochMillis] falls in — the same count [answerDays]
 * would report for it, without building the whole history to read one day.
 */
internal fun answersOn(
    scheduling: Map<String, CardScheduling>,
    nowEpochMillis: Long,
    tzId: String,
): Int {
    val zone = zoneOf(tzId)
    val date = localDate(nowEpochMillis, tzId)
    val start = date.atStartOfDayIn(zone)
    val end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    return scheduling.values.sumOf { sched ->
        sched.log.count { it.date >= start && it.date < end }
    }
}
