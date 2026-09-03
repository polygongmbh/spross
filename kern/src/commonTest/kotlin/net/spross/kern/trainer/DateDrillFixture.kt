package net.spross.kern.trainer

import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry
import net.spross.kern.catalog.DateNames
import net.spross.kern.catalog.DatePattern
import net.spross.kern.catalog.DatePatterns
import net.spross.kern.model.LanguageInfo

/**
 * Hand-built calendars for the drill and run tests: an en→de pair carrying the full
 * ladder (a `dateWithYear`, the article patterns, a synonym on Samstag and Januar, the
 * distance-1 `Juni`/`Juli`), a de→uk pair carrying the short one (a `dateForm` on
 * every month, no year pattern) and a de→en pair whose patterns carry a SYNONYM — the
 * three shapes `docs/date-readings.md` says the content can take. Every target is a pack
 * language, so the generated Sprossen draw.
 */
internal object DateDrillFixture {

    val german = LanguageInfo(
        code = "de", name = "Deutsch", englishName = "German", flag = "🇩🇪",
        articles = listOf("der", "die", "das", "ein", "eine"),
    )

    val english = LanguageInfo(
        code = "en", name = "English", englishName = "English", flag = "🇬🇧",
        articles = listOf("the", "a", "an"),
    )

    val ukrainian = LanguageInfo(code = "uk", name = "Українська", englishName = "Ukrainian", flag = "🇺🇦")

    private val enWeekdays = names(
        "Monday" to "Mon", "Tuesday" to "Tue", "Wednesday" to "Wed", "Thursday" to "Thu",
        "Friday" to "Fri", "Saturday" to "Sat", "Sunday" to "Sun",
    )

    private val enMonths = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ).map { DateNames(it) }

    private val deWeekdays = names(
        "Montag" to "Mo", "Dienstag" to "Di", "Mittwoch" to "Mi", "Donnerstag" to "Do",
        "Freitag" to "Fr", "Samstag" to "Sa", "Sonntag" to "So",
    ).map { if (it.text == "Samstag") it.copy(synonyms = listOf("Sonnabend")) else it }

    private val deMonths = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    ).map { if (it == "Januar") DateNames(it, synonyms = listOf("Jänner")) else DateNames(it) }

    private val ukWeekdays = names(
        "понеділок" to "пн", "вівторок" to "вт", "середа" to "ср", "четвер" to "чт",
        "п'ятниця" to "пт", "субота" to "сб", "неділя" to "нд",
    )

    private val ukMonths = listOf(
        "січень" to "січня", "лютий" to "лютого", "березень" to "березня",
        "квітень" to "квітня", "травень" to "травня", "червень" to "червня",
        "липень" to "липня", "серпень" to "серпня", "вересень" to "вересня",
        "жовтень" to "жовтня", "листопад" to "листопада", "грудень" to "грудня",
    ).map { (text, dateForm) -> DateNames(text, dateForm = dateForm) }

    /** en→de: the full seven-Sprosse ladder, answers in German. */
    val germanContent = DateDrillContent(
        source = "en",
        target = "de",
        weekdays = entries(enWeekdays, deWeekdays),
        months = entries(enMonths, deMonths),
        numeric = "{m}/{d}/{y}",
        patterns = DatePatterns(
            dayMonth = DatePattern("der {day} {month}", variants = listOf("den {day} {month}")),
            date = DatePattern(
                "{weekday}, der {day} {month}",
                variants = listOf("{weekday}, den {day} {month}"),
            ),
            dateWithYear = DatePattern(
                "{weekday}, der {day} {month} {year}",
                variants = listOf("{weekday}, den {day} {month} {year}"),
            ),
        ),
    )

    /** de→en: the one language that SAYS its date two ways, so the reveal has a turn to take. */
    val englishContent = DateDrillContent(
        source = "de",
        target = "en",
        weekdays = entries(deWeekdays, enWeekdays),
        months = entries(deMonths, enMonths),
        numeric = "{d}.{m}.{y}",
        patterns = DatePatterns(
            dayMonth = DatePattern(
                "{month} {day}",
                synonyms = listOf("the {day} of {month}"),
                variants = listOf("{day} of {month}"),
            ),
            date = DatePattern(
                "{weekday}, {month} {day}",
                synonyms = listOf("{weekday}, the {day} of {month}"),
            ),
            dateWithYear = null,
        ),
    )

    /** de→uk: no `dateWithYear`, so the ladder tops out a Sprosse short; months decline. */
    val ukrainianContent = DateDrillContent(
        source = "de",
        target = "uk",
        weekdays = entries(deWeekdays, ukWeekdays),
        months = entries(deMonths, ukMonths),
        numeric = "{d}.{m}.{y}",
        patterns = DatePatterns(
            dayMonth = DatePattern("{day} {month}"),
            date = DatePattern("{weekday}, {day} {month}"),
            dateWithYear = null,
        ),
    )

    private fun names(vararg pairs: Pair<String, String>): List<DateNames> =
        pairs.map { (text, abbr) -> DateNames(text, abbr = abbr) }

    private fun entries(source: List<DateNames>, target: List<DateNames>): List<DateEntry> =
        source.zip(target).mapIndexed { index, (s, t) -> DateEntry(index, s, t) }
}
