package net.spross.kern.trainer

import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry
import net.spross.kern.catalog.DateNames
import net.spross.kern.catalog.DatePattern
import net.spross.kern.catalog.DateNumeral
import net.spross.kern.catalog.DatePatterns
import net.spross.kern.model.LanguageInfo

/**
 * Hand-built calendars for the drill and run tests: an en→de pair carrying the full
 * ladder (a `dateWithYear`, the article patterns, a synonym on Samstag and Januar, the
 * distance-1 `Juni`/`Juli`), a de→uk pair carrying the short one (a `dateForm` on
 * every month, no year pattern), a de→en pair whose patterns carry a SYNONYM, and a de→sw
 * pair whose numerals sit one edit apart (`sita`/`saba`) and whose spans count with a
 * cardinal — the shapes `docs/date-readings.md` says the content can take. Ukrainian carries
 * neither a year nor a span, so it is the ladder that stops short at both ends. Every target is a pack language, so
 * the generated Sprossen draw.
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

    val swahili = LanguageInfo(code = "sw", name = "Kiswahili", englishName = "Swahili", flag = "🇹🇿")

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

    private val swWeekdays = names(
        "Jumatatu" to "Jtt", "Jumanne" to "Jnn", "Jumatano" to "Jtn", "Alhamisi" to "Alh",
        "Ijumaa" to "Ijm", "Jumamosi" to "Jmo", "Jumapili" to "Jpl",
    )

    private val swMonths = listOf(
        "Januari", "Februari", "Machi", "Aprili", "Mei", "Juni",
        "Julai", "Agosti", "Septemba", "Oktoba", "Novemba", "Desemba",
    ).map { DateNames(it) }

    /** en→de: the whole ladder, answers in German. */
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
            century = DatePattern("das {count} Jahrhundert", numeral = DateNumeral.Ordinal),
            millennium = DatePattern("das {count} Jahrtausend", numeral = DateNumeral.Ordinal),
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

    /** de→sw: a pattern word in front of the day, and numerals one edit from each other. */
    val swahiliContent = DateDrillContent(
        source = "de",
        target = "sw",
        weekdays = entries(deWeekdays, swWeekdays),
        months = entries(deMonths, swMonths),
        numeric = "{d}.{m}.{y}",
        patterns = DatePatterns(
            dayMonth = DatePattern("tarehe {day} {month}"),
            date = DatePattern("{weekday}, tarehe {day} {month}"),
            dateWithYear = DatePattern("{weekday}, tarehe {day} {month} mwaka wa {year}"),
            century = DatePattern("karne ya {count}", numeral = DateNumeral.Cardinal),
            millennium = DatePattern("milenia ya {count}", numeral = DateNumeral.Cardinal),
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
