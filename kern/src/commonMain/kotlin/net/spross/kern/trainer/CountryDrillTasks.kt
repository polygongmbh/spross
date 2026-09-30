package net.spross.kern.trainer

import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent

/**
 * How a question of the atlas becomes a [CountryDrillTask]: prompt, accepted set, display and
 * gloss off the joined rows. [CountryDrill] owns WHAT a Sprosse asks; this object owns what it
 * looks like.
 */
internal object CountryDrillTasks {

    /** Every question of [kinds] over the rows up to [ceiling], in a stable order. */
    fun build(
        content: CountryDrillContent,
        ceiling: Int,
        kinds: List<CountryTaskKind>,
        reverse: Boolean,
    ): List<CountryDrillTask> {
        val countries = content.countries.filter { it.tier <= ceiling }
        val languages = content.languages.filter { it.tier <= ceiling }
        return kinds.flatMap { kind ->
            when (kind) {
                // why: a name both sides spell alike is no question — the prompt IS the
                // answer. The fallback holds a pair whose every name matches: a Sprosse with
                // nothing in it would be worse than an easy one.
                CountryTaskKind.CountryName ->
                    countries.filter { it.namesDiffer() }.ifEmpty { countries }
                        .map { countryName(it, reverse) }
                CountryTaskKind.Nationality -> countries.map { nationality(it, reverse) }
                CountryTaskKind.FlagCountry -> countries.map { flagCountry(it, reverse) }
                CountryTaskKind.LanguageName -> languages.map { languageName(it, reverse) }
                CountryTaskKind.SpokenIn -> countries.mapNotNull { spokenIn(content, it, ceiling, reverse) }
                CountryTaskKind.SpokenWhere -> languages.mapNotNull { spokenWhere(content, it, ceiling, reverse) }
            }
        }
    }

    private fun countryName(country: AtlasCountryEntry, reverse: Boolean): CountryDrillTask {
        val answer = country.answer(reverse)
        return CountryDrillTask(
            kind = CountryTaskKind.CountryName,
            id = country.slug,
            promptText = country.prompt(reverse).text,
            promptEmoji = country.flag,
            emojiIsGiveaway = reverse,
            accepted = listOf(answer.text) + answer.accepts,
            display = answer.text,
            gloss = answer.nationality.text,
        )
    }

    /**
     * The flag alone, with no name on the card at all — the outer Sprossen' recognition game.
     * Every country stands here, including the ones [namesDiffer] keeps out of the name
     * questions: nothing is written down to give the answer away, so "Venezuela" is a
     * question again. The reveal names the country on the asking side too, or a miss would
     * leave the learner not knowing which flag that was.
     */
    private fun flagCountry(country: AtlasCountryEntry, reverse: Boolean): CountryDrillTask {
        val answer = country.answer(reverse)
        return CountryDrillTask(
            kind = CountryTaskKind.FlagCountry,
            id = country.slug,
            promptText = null,
            promptEmoji = country.flag,
            accepted = listOf(answer.text) + answer.accepts,
            display = answer.text,
            gloss = country.prompt(reverse).text,
        )
    }

    private fun nationality(country: AtlasCountryEntry, reverse: Boolean): CountryDrillTask {
        val answer = country.answer(reverse).nationality
        return CountryDrillTask(
            kind = CountryTaskKind.Nationality,
            id = country.slug,
            promptText = country.prompt(reverse).nationality.text,
            promptEmoji = country.flag,
            emojiIsGiveaway = reverse,
            accepted = listOf(answer.text) + answer.accepts,
            display = answer.text,
            gloss = country.answer(reverse).text,
        )
    }

    private fun languageName(language: AtlasLanguageEntry, reverse: Boolean): CountryDrillTask {
        val answer = language.answer(reverse)
        return CountryDrillTask(
            kind = CountryTaskKind.LanguageName,
            id = language.code,
            promptText = language.prompt(reverse).name,
            promptEmoji = null,
            accepted = listOf(answer.name) + answer.accepts,
            display = answer.name,
            gloss = null,
        )
    }

    /**
     * Country → language. EVERY language the country speaks is accepted, including ones
     * the Sprosse has not reached: "French" is a true answer about Switzerland whether or not
     * the ladder has opened tier 3 yet. The reveal shows one the learner has met.
     */
    private fun spokenIn(
        content: CountryDrillContent,
        country: AtlasCountryEntry,
        ceiling: Int,
        reverse: Boolean,
    ): CountryDrillTask? {
        val spoken = content.languagesOf(country).ifEmpty { return null }
        val shown = spoken.firstOrNull { it.tier <= ceiling } ?: spoken.first()
        val display = shown.answer(reverse).name
        val forms = spoken.flatMap { listOf(it.answer(reverse).name) + it.answer(reverse).accepts }
        return CountryDrillTask(
            kind = CountryTaskKind.SpokenIn,
            id = country.slug,
            promptText = country.prompt(reverse).text,
            promptEmoji = country.flag,
            emojiIsGiveaway = reverse,
            accepted = (listOf(display) + forms).distinct(),
            display = display,
            gloss = country.answer(reverse).text,
        )
    }

    /** Language → country, [spokenIn] read backwards; the same union rule applies. */
    private fun spokenWhere(
        content: CountryDrillContent,
        language: AtlasLanguageEntry,
        ceiling: Int,
        reverse: Boolean,
    ): CountryDrillTask? {
        val spoken = content.countriesOf(language).ifEmpty { return null }
        val shown = spoken.firstOrNull { it.tier <= ceiling } ?: spoken.first()
        val display = shown.answer(reverse).text
        val forms = spoken.flatMap { listOf(it.answer(reverse).text) + it.answer(reverse).accepts }
        return CountryDrillTask(
            kind = CountryTaskKind.SpokenWhere,
            id = language.code,
            promptText = language.prompt(reverse).name,
            promptEmoji = null,
            accepted = (listOf(display) + forms).distinct(),
            display = display,
            gloss = language.answer(reverse).name,
        )
    }

    /**
     * Whether the two languages actually call this country something DIFFERENT. Asking a
     * de→es learner for "Venezuela" teaches nothing: the prompt is already the answer, and
     * typing it straight back would be graded correct.
     *
     * Compared over every accepted form on both sides — so a name that differs only by an
     * article the other side also accepts counts as the same — and blind to case and to
     * accents, which makes "Peru"/"Perú" one name and "Kenia"/"Kenya" two.
     */
    private fun AtlasCountryEntry.namesDiffer(): Boolean {
        val known = (listOf(source.text) + source.accepts).mapTo(mutableSetOf()) { fold(it) }
        return (listOf(target.text) + target.accepts).none { fold(it) in known }
    }

    /** Casefolded, stripped of accents and of everything that is not a letter or a digit. */
    private fun fold(raw: String): String = buildString {
        for (char in raw.lowercase()) {
            val plain = ACCENTS[char] ?: char.toString()
            for (letter in plain) if (letter.isLetterOrDigit()) append(letter)
        }
    }

    /**
     * The accents the atlas actually carries, plus the Latin ones a new language would
     * bring. Kotlin common has no Unicode decomposition, so the map IS the rule.
     */
    private val ACCENTS: Map<Char, String> = buildMap {
        fun spread(plain: String, accented: String) = accented.forEach { put(it, plain) }
        spread("a", "áàâãäåā")
        spread("c", "çćč")
        spread("e", "éèêëē")
        spread("i", "íìîïīı")
        spread("n", "ñń")
        spread("o", "óòôõöøō")
        spread("u", "úùûüū")
        spread("y", "ýÿ")
        spread("s", "śšș")
        spread("z", "źżž")
        spread("g", "ğ")
        spread("l", "ł")
        spread("d", "đð")
        spread("r", "ř")
        spread("t", "ț")
        put('ß', "ss")
        put('æ', "ae")
        put('œ', "oe")
        put('þ', "th")
    }

    private fun AtlasCountryEntry.prompt(reverse: Boolean) = if (reverse) target else source

    private fun AtlasCountryEntry.answer(reverse: Boolean) = if (reverse) source else target

    private fun AtlasLanguageEntry.prompt(reverse: Boolean) = if (reverse) target else source

    private fun AtlasLanguageEntry.answer(reverse: Boolean) = if (reverse) source else target
}
