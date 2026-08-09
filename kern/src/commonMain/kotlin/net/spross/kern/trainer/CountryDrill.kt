package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent

/**
 * The atlas drill: name the country, the people, the language — and say which is spoken
 * where. Typed answers only, in both directions.
 *
 * Registry-by-file like the letter drill: a pair has this drill exactly when
 * [net.spross.kern.catalog.Catalog.countryDrillContent] joins something for it. No
 * [TrainerKind], no [DrillVariant] — a different skill, not another way of playing numbers.
 *
 * Everything here is pure and stateless: no schedule is read, no review is booked. Sampling
 * takes an injected [Random], so both platforms derive the same run from the same seed and
 * the ladder is pinned in tests rather than described twice in two UI layers.
 *
 * The ladder widens OUTWARD from the learner's own two languages, each rung keeping
 * everything below it:
 *
 * | rung | pool | asks |
 * |---|---|---|
 * | 1 | the profile's own languages and their countries (tier 1) | the country's name, where the two languages differ on it |
 * | 2 | tier 1 | + the language's, + the people's |
 * | 3 | + tier 2 | + which language is spoken there |
 * | 4 | + tier 3 | + the country behind a flag alone |
 * | 5 | + tier 4 | |
 * | 6 | everything | + where a language is spoken |
 *
 * A tier the catalog has not authored yet costs nothing: the pool is the join intersected
 * with the ceiling, so an empty new tier simply repeats the pool below it.
 */
object CountryDrill {
    const val MAX_LEVEL = 6

    /** Two clean wins a rung — the classic, with no held-vocabulary shortcut to earn. */
    const val WINS_TO_ADVANCE = 2

    /** The rung ramp, on the ladder's own ceiling and rung length ([DrillRamp.step]). */
    fun step(level: Int, winsAtLevel: Int, correct: Boolean, clean: Boolean): DrillRamp.RungStep =
        DrillRamp.step(level, winsAtLevel, correct, clean, MAX_LEVEL, WINS_TO_ADVANCE)

    /** How far out [level] reaches — tier 1 is the profile's own, 4 the regional rest. */
    fun tierCeiling(level: Int): Int = when (level.coerceIn(1, MAX_LEVEL)) {
        1, 2 -> 1
        3 -> 2
        4 -> 3
        else -> 4
    }

    /** What [level] may ask, in ladder order. */
    fun kinds(level: Int): List<CountryTaskKind> = when (level.coerceIn(1, MAX_LEVEL)) {
        1 -> listOf(CountryTaskKind.CountryName)
        2 -> listOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName, CountryTaskKind.Nationality)
        3 -> listOf(
            CountryTaskKind.CountryName,
            CountryTaskKind.LanguageName,
            CountryTaskKind.Nationality,
            CountryTaskKind.SpokenIn,
        )
        4, 5 -> listOf(
            CountryTaskKind.CountryName,
            CountryTaskKind.LanguageName,
            CountryTaskKind.Nationality,
            CountryTaskKind.FlagCountry,
            CountryTaskKind.SpokenIn,
        )
        else -> CountryTaskKind.entries
    }

    /**
     * Every question [level] could ask, in a stable order — the rung's pool, made explicit.
     * [reverse] flips which side prompts: forward asks in the language the learner KNOWS,
     * reversed asks in the one they are learning and grades in their own.
     *
     * Where the ceiling's pool builds nothing at all — a catalog whose inner tiers are not
     * authored yet — it widens until something stands, because a rung with no question is
     * not a rung the learner can climb off.
     */
    fun tasks(content: CountryDrillContent, level: Int, reverse: Boolean = false): List<CountryDrillTask> {
        val kinds = kinds(level)
        var ceiling = tierCeiling(level)
        while (true) {
            val built = build(content, ceiling, kinds, reverse)
            if (built.isNotEmpty() || ceiling >= content.widestTier) return built
            ceiling++
        }
    }

    /**
     * One question from [level]'s pool. [avoidId] is the previous answer's id, resampled
     * once so a repeat needs two unlucky draws rather than one — the letter drill's rule.
     */
    fun sample(
        content: CountryDrillContent,
        level: Int,
        reverse: Boolean,
        avoidId: String?,
        rng: Random,
    ): CountryDrillTask {
        val pool = tasks(content, level, reverse)
        require(pool.isNotEmpty()) { "no atlas question at rung $level for ${content.source}→${content.target}" }
        var picked = pool[rng.nextInt(pool.size)]
        if (picked.id == avoidId) picked = pool[rng.nextInt(pool.size)]
        return picked
    }

    /**
     * The overview table, from the same joined rows the drill grades against — a reference
     * that cannot drift from the run, because there is nothing for it to drift from.
     */
    fun reference(content: CountryDrillContent): List<CountryReferenceGroup> =
        content.countries.groupBy { it.tier }.entries.sortedBy { it.key }.map { (tier, countries) ->
            CountryReferenceGroup(
                tier = tier,
                rows = countries.map { country ->
                    val spoken = content.languagesOf(country)
                    CountryReferenceRow(
                        slug = country.slug,
                        flag = country.flag,
                        source = country.source.text,
                        target = country.target.text,
                        sourceNationality = country.source.nationality.text,
                        targetNationality = country.target.nationality.text,
                        sourceLanguages = spoken.map { it.source.name },
                        targetLanguages = spoken.map { it.target.name },
                    )
                },
            )
        }

    private fun build(
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
                // answer. The fallback holds a pair whose every name matches: a rung with
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
            accepted = listOf(answer.text) + answer.variants,
            display = answer.text,
            gloss = answer.nationality.text,
        )
    }

    /**
     * The flag alone, with no name on the card at all — the outer rungs' recognition game.
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
            accepted = listOf(answer.text) + answer.variants,
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
            accepted = listOf(answer.text) + answer.variants,
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
            accepted = listOf(answer.name) + answer.variants,
            display = answer.name,
            gloss = null,
        )
    }

    /**
     * Country → language. EVERY language the country speaks is accepted, including ones
     * the rung has not reached: "French" is a true answer about Switzerland whether or not
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
        val forms = spoken.flatMap { listOf(it.answer(reverse).name) + it.answer(reverse).variants }
        return CountryDrillTask(
            kind = CountryTaskKind.SpokenIn,
            id = country.slug,
            promptText = country.prompt(reverse).text,
            promptEmoji = country.flag,
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
        val forms = spoken.flatMap { listOf(it.answer(reverse).text) + it.answer(reverse).variants }
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
        val known = (listOf(source.text) + source.variants).mapTo(mutableSetOf()) { fold(it) }
        return (listOf(target.text) + target.variants).none { fold(it) in known }
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
