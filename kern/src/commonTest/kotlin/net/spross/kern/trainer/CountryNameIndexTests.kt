package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.CountryName
import net.spross.kern.catalog.LanguageName
import net.spross.kern.catalog.NationalityName
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * The country drill's refusal check, on the atlas's real confusable shape: Switzerland and
 * Sweden are one edit apart in Swahili (`Uswisi`/`Uswidi`, `Waswisi`/`Waswidi`), and a
 * scope may never see another scope's names.
 */
class CountryNameIndexTests {

    private val content = CountryDrillContent(
        source = "de",
        target = "sw",
        countries = listOf(
            entry("switzerland", "🇨🇭", "die Schweiz", "Schweizer", "Uswisi", "Waswisi"),
            entry("sweden", "🇸🇪", "Schweden", "Schweden", "Uswidi", "Waswidi"),
            entry("spain", "🇪🇸", "Spanien", "Spanier", "Uhispania", "Wahispania"),
        ),
        languages = listOf(
            AtlasLanguageEntry(
                code = "sv",
                tier = 2,
                source = LanguageName("Schwedisch", "auf Schwedisch"),
                target = LanguageName("Kiswidi", "kwa Kiswidi"),
            ),
        ),
    )

    private val config = CountryDrillRunConfig(
        content = content,
        reverse = false,
        fast = false,
        normalizer = AnswerNormalizer.drill(
            LanguageInfo(code = "sw", name = "Kiswahili", englishName = "Swahili", flag = "🇹🇿"),
        ),
    )

    @Test
    fun anotherCountrysNameIsRefusedAndNamedByItsPromptSide() {
        val match = CountryDrillRun.grade("Uswidi", countryTask(), config)
        assertIs<Match.OtherWord>(match)
        assertEquals("Uswidi", match.word)
        assertEquals(listOf("Schweden"), match.meanings)
    }

    @Test
    fun anotherPeoplesNameIsRefusedInTheNationalityScope() {
        val task = CountryDrillTask(
            kind = CountryTaskKind.Nationality,
            id = "switzerland",
            promptText = "Schweizer",
            promptEmoji = "🇨🇭",
            accepted = listOf("Waswisi"),
            display = "Waswisi",
            gloss = null,
        )
        val match = CountryDrillRun.grade("Waswidi", task, config)
        assertIs<Match.OtherWord>(match)
        assertEquals(listOf("Schweden"), match.meanings)
    }

    @Test
    fun aScopeNeverSeesAnotherScopesNames() {
        // sw peoples wear the Wa- prefix, so the cross-scope shape needs the de side:
        // Spanien (country) and Spanier (people) are one edit apart, and a reversed run
        // answers in German. The people's name must stay a near-miss of the country's.
        val reversed = CountryDrillRunConfig(
            content = content,
            reverse = true,
            fast = false,
            normalizer = AnswerNormalizer.drill(
                LanguageInfo(
                    code = "de", name = "Deutsch", englishName = "German", flag = "🇩🇪",
                    articles = listOf("der", "die", "das"),
                ),
            ),
        )
        val task = CountryDrillTask(
            kind = CountryTaskKind.CountryName,
            id = "spain",
            promptText = "Uhispania",
            promptEmoji = "🇪🇸",
            accepted = listOf("Spanien"),
            display = "Spanien",
            gloss = null,
        )
        assertIs<Match.Typo>(CountryDrillRun.grade("Spanier", task, reversed))
    }

    @Test
    fun aWrongAnswerThatIsAnotherCountryWholeIsNamedToo() {
        // Uhispania is far beyond the typo budget at Uswisi — named, not just wrong.
        val match = CountryDrillRun.grade("Uhispania", countryTask(), config)
        assertIs<Match.OtherWord>(match)
        assertEquals(listOf("Spanien"), match.meanings)
    }

    @Test
    fun aFumbleNamingNoEntryStaysATypo() {
        assertIs<Match.Typo>(CountryDrillRun.grade("Uswiisi", countryTask(), config))
    }

    private fun countryTask() = CountryDrillTask(
        kind = CountryTaskKind.CountryName,
        id = "switzerland",
        promptText = "die Schweiz",
        promptEmoji = "🇨🇭",
        accepted = listOf("Uswisi"),
        display = "Uswisi",
        gloss = null,
    )

    private fun entry(
        slug: String,
        flag: String,
        de: String,
        dePeople: String,
        sw: String,
        swPeople: String,
    ) = AtlasCountryEntry(
        slug = slug,
        flag = flag,
        tier = 1,
        languages = emptyList(),
        source = CountryName(text = de, nationality = NationalityName(dePeople)),
        target = CountryName(text = sw, nationality = NationalityName(swPeople)),
    )
}
