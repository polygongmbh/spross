package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry
import net.spross.kern.catalog.DateNames
import net.spross.kern.catalog.DatePattern
import net.spross.kern.catalog.DatePatterns
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match
import net.spross.kern.session.TurnFeedback

/**
 * The dates drill's refusal check, on the calendar's real confusable shape: `Juni` and
 * `Juli` are one edit apart, the single-card typo budget would certify exactly the
 * confusion the drill exists to fix, and the owner's ruling grades strictness by how much
 * of the answer the confusable word is — the whole of it on a bare Sprosse, one word of an
 * assembled date above them.
 */
class DateNameIndexTests {

    private val config = DateDrillRunConfig(
        content = DateDrillFixture.germanContent,
        reverse = false,
        fast = false,
        normalizer = AnswerNormalizer.drill(DateDrillFixture.german),
    )

    private fun monthTask(index: Int) =
        DateDrillTasks.pool(DateDrillFixture.germanContent, DateTaskKind.Month, reverse = false)[index]

    /** Where the whole answer is the name, `Juli` for `Juni` is July — refused and named. */
    @Test
    fun aBareMonthThatIsAnotherMonthIsRefusedWithItsMeaning() {
        val match = DateDrillRun.grade("Juli", monthTask(5), config)
        assertIs<Match.OtherWord>(match)
        assertEquals("Juli", match.word)
        assertEquals(listOf("July"), match.meanings)
    }

    /** The refusal reaches the run's state, so the reveal can say what was written. */
    @Test
    fun aRefusedBareNameLandsRevealedWithItsOtherWord() {
        val run = DateDrillRun.openAt(config, 2, Random(7)).copy(task = monthTask(5))
        val reduction = DateDrillRun.reduce(run, DateDrillIntent.Submit("Juli"), Random(7))
        assertEquals(TurnFeedback.Revealed, reduction.state.feedback)
        assertEquals("Juli", assertNotNull(reduction.state.otherWord).word)
    }

    /** A synonym is a name's own form everywhere, the index included. */
    @Test
    fun aSynonymRefusesForItsOwnerToo() {
        val sunday = DateDrillTasks.pool(DateDrillFixture.germanContent, DateTaskKind.Weekday, false)[6]
        val match = DateDrillRun.grade("Sonnabend", sunday, config)
        assertIs<Match.OtherWord>(match)
        assertEquals(listOf("Saturday"), match.meanings)
    }

    /** A fumble spelling NO name stays the forgiven slip it always was. */
    @Test
    fun aFumbleNamingNoNameStaysATypo() {
        assertIs<Match.Typo>(DateDrillRun.grade("Juki", monthTask(5), config))
    }

    /** The drill normalizer forgives no article — pinned, because the bare Sprosse leans on it. */
    @Test
    fun anArticleTheCalendarDidNotAuthorGradesWrong() {
        assertEquals(Match.Wrong, DateDrillRun.grade("der Juni", monthTask(5), config))
    }

    /** The pattern's `den` variant is what admits the accusative — as an Exact, not a slip. */
    @Test
    fun thePatternVariantAdmitsTheAccusative() {
        val task = DateDrillTasks.dayMonth(DateDrillFixture.germanContent, 3, 5)
        assertEquals(Match.Exact, DateDrillRun.grade("den dritten Juni", task, config))
    }

    /**
     * A month slip INSIDE an assembled date books a typo — the owner's ruling: a learner
     * who assembled the whole date and slipped inside one word got the structure right,
     * so the index is never consulted above the bare Sprossen.
     */
    @Test
    fun anAssembledDateKeepsItsBridge() {
        val task = DateDrillTasks.dayMonth(DateDrillFixture.germanContent, 3, 5)
        val match = DateDrillRun.grade("der dritte Juli", task, config)
        assertIs<Match.Typo>(match)
        assertEquals("der dritte Juni", match.corrected)
    }

    /**
     * The one merged space, catching what kind-scoping would miss: eo `mardo` (Tuesday)
     * and `marto` (March) sit one edit apart ACROSS the weekday/month line.
     */
    @Test
    fun aWeekdayAnswerThatSpellsAMonthIsRefusedAcrossTheSpace() {
        val esperanto = DateDrillContent(
            source = "en",
            target = "eo",
            weekdays = listOf(
                DateEntry(0, DateNames("Monday", abbr = "Mon"), DateNames("lundo", abbr = "lun")),
                DateEntry(1, DateNames("Tuesday", abbr = "Tue"), DateNames("mardo", abbr = "mar")),
            ),
            months = listOf(
                DateEntry(0, DateNames("January"), DateNames("januaro")),
                DateEntry(1, DateNames("February"), DateNames("februaro")),
                DateEntry(2, DateNames("March"), DateNames("marto")),
            ),
            numeric = "{y}-{m}-{d}",
            patterns = DatePatterns(
                dayMonth = DatePattern("la {day} de {month}"),
                date = DatePattern("{weekday}, la {day} de {month}"),
                dateWithYear = null,
            ),
        )
        val eoConfig = DateDrillRunConfig(
            content = esperanto,
            reverse = false,
            fast = false,
            normalizer = AnswerNormalizer.drill(
                LanguageInfo(code = "eo", name = "Esperanto", englishName = "Esperanto",
                    flag = "💚", articles = listOf("la")),
            ),
        )
        val tuesday = DateDrillTasks.pool(esperanto, DateTaskKind.Weekday, reverse = false)[1]
        val match = DateDrillRun.grade("marto", tuesday, eoConfig)
        assertIs<Match.OtherWord>(match)
        assertEquals(listOf("March"), match.meanings)
    }

    /**
     * The asked entry is skipped: uk `березня` is owned by March alone, so typed AT March
     * it stays March's own plain miss — the citation name was asked — while typed at any
     * other month it is March whole and the refusal says so.
     */
    @Test
    fun aDateFormOwnedOnlyByTheAskedEntryStaysAPlainMiss() {
        val ukConfig = DateDrillRunConfig(
            content = DateDrillFixture.ukrainianContent,
            reverse = false,
            fast = false,
            normalizer = AnswerNormalizer.drill(DateDrillFixture.ukrainian),
        )
        val months = DateDrillTasks.pool(DateDrillFixture.ukrainianContent, DateTaskKind.Month, false)
        assertEquals(Match.Wrong, DateDrillRun.grade("березня", months[2], ukConfig))

        val atJuly = DateDrillRun.grade("березня", months[6], ukConfig)
        assertIs<Match.OtherWord>(atJuly)
        assertEquals(listOf("März"), atJuly.meanings, "named by its prompt-side (de) name")
    }

    /**
     * The day-1 value check fires: fr `un` (the cardinal) and `premier` (the date's own
     * reading) are disjoint identities in the number index, so the cardinal is refused
     * rather than forgiven — `le premier mars`, never `le un mars`.
     */
    @Test
    fun theFrenchFirstRefusesItsCardinal() {
        val french = DateDrillContent(
            source = "en",
            target = "fr",
            weekdays = listOf(
                DateEntry(0, DateNames("Monday", abbr = "Mon"), DateNames("lundi", abbr = "lun")),
            ),
            months = listOf(
                DateEntry(0, DateNames("March"), DateNames("mars")),
            ),
            numeric = "{d}/{m}/{y}",
            patterns = DatePatterns(
                dayMonth = DatePattern("le {day} {month}"),
                date = DatePattern("{weekday} {day} {month}"),
                dateWithYear = null,
            ),
        )
        val frConfig = DateDrillRunConfig(
            content = french,
            reverse = false,
            fast = false,
            normalizer = AnswerNormalizer.drill(
                LanguageInfo(code = "fr", name = "Français", englishName = "French", flag = "🇫🇷",
                    articles = listOf("le", "la", "les", "l'", "un", "une")),
            ),
        )
        val task = DateDrillTasks.day(french, 1)
        assertEquals(listOf("premier"), task.accepted)
        val match = DateDrillRun.grade("un", task, frConfig)
        assertIs<Match.OtherWord>(match)
        assertEquals("un", match.word)
    }
}
