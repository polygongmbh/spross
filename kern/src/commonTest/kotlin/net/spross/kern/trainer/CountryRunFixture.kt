package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.CountryName
import net.spross.kern.catalog.LanguageName
import net.spross.kern.catalog.NationalityName
import net.spross.kern.model.LanguageInfo
import net.spross.kern.session.AnswerNormalizer

/**
 * A de→sw atlas of ONE tier-1 country for the run tests: Sprosse 1 has exactly one question,
 * so every assertion reads a task it can predict.
 */
internal object CountryRunFixture {

    val content = CountryDrillContent(
        source = "de",
        target = "sw",
        countries = listOf(
            AtlasCountryEntry(
                slug = "germany",
                flag = "🇩🇪",
                tier = 1,
                languages = listOf("de"),
                source = CountryName(text = "Deutschland", nationality = NationalityName("Deutsche")),
                target = CountryName(text = "Ujerumani", nationality = NationalityName("Wajerumani")),
            ),
        ),
        languages = listOf(
            AtlasLanguageEntry(
                code = "de",
                tier = 1,
                source = LanguageName("Deutsch", "auf Deutsch"),
                target = LanguageName("Kijerumani", "kwa Kijerumani"),
            ),
            AtlasLanguageEntry(
                code = "sw",
                tier = 1,
                source = LanguageName("Suaheli", "auf Suaheli"),
                target = LanguageName("Kiswahili", "kwa Kiswahili"),
            ),
        ),
    )

    private val swahili = LanguageInfo(code = "sw", name = "Kiswahili", englishName = "Swahili", flag = "🇹🇿")

    private val german = LanguageInfo(
        code = "de",
        name = "Deutsch",
        englishName = "German",
        flag = "🇩🇪",
        articles = listOf("der", "die", "das"),
    )

    fun config(
        reverse: Boolean = false,
        fast: Boolean = false,
        graded: Boolean = true,
        cleared: Set<Int> = emptySet(),
    ) = CountryDrillRunConfig(
        content = content,
        reverse = reverse,
        fast = fast,
        normalizer = when {
            !graded -> null
            reverse -> AnswerNormalizer.drill(german)
            else -> AnswerNormalizer.drill(swahili)
        },
        cleared = cleared,
    )

    fun open(
        reverse: Boolean = false,
        fast: Boolean = false,
        graded: Boolean = true,
        sprosse: Int = 1,
        cleared: Set<Int> = emptySet(),
    ) = CountryDrillRun.openAt(config(reverse, fast, graded, cleared), sprosse, Random(7))
}
