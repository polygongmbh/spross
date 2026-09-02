package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.model.Language

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
 * everything below it — and each rung brings exactly ONE new thing, either a question or a
 * tier, never both at once:
 *
 * | rung | pool | asks |
 * |---|---|---|
 * | 1 | the profile's own languages and their countries (tier 1) | the country's name, where the two languages differ on it |
 * | 2 | tier 1 | + the language's name |
 * | 3 | tier 1 | + the people's name |
 * | 4 | + tier 2 | |
 * | 5 | tier 2 | + which language is spoken there |
 * | 6 | + tier 3 | |
 * | 7 | tier 3 | + the country behind a flag alone (forward runs only) |
 * | 8 | + tier 4 | |
 * | 9 | everything | + where a language is spoken |
 *
 * A tier the catalog has not authored yet costs nothing: the pool is the join intersected
 * with the ceiling, so an empty new tier simply repeats the pool below it. Rung 7 is that
 * same nothing in a REVERSED run, where the flag question does not exist — see [kinds].
 */
object CountryDrill {
    const val MAX_LEVEL = 9

    /** Three clean wins a rung: more rungs, and more rows standing on each of them. */
    const val WINS_TO_ADVANCE = 3

    /**
     * How LONG a rung is. Fast spends one clean win instead of the three, and is the reward
     * for having topped the ladder the hard way ([fastUnlocked]) — the numbers drill's rule
     * ([Trainer.winsToAdvance]), read off this ladder's own pacing.
     */
    fun winsToAdvance(fast: Boolean): Int = if (fast) 1 else WINS_TO_ADVANCE

    /**
     * Whether the Fast modifier is on offer at all. Having EVER stood on the top rung is the
     * price — [bestLevel] is the highest rung any run reached, which is what the app keeps.
     */
    fun fastUnlocked(bestLevel: Int): Boolean = bestLevel >= MAX_LEVEL

    /** The rung ramp, on the ladder's rung length ([DrillRamp.step]). */
    fun step(
        level: Int,
        winsAtLevel: Int,
        correct: Boolean,
        clean: Boolean,
        fast: Boolean = false,
    ): DrillRamp.RungStep =
        DrillRamp.step(level, winsAtLevel, correct, clean, winsToAdvance(fast))

    /** How far out [level] reaches — tier 1 is the profile's own, 4 the regional rest. */
    fun tierCeiling(level: Int): Int = when (level.coerceIn(1, MAX_LEVEL)) {
        1, 2, 3 -> 1
        4, 5 -> 2
        6, 7 -> 3
        else -> 4
    }

    /**
     * What [level] may ask, in ladder order.
     *
     * A REVERSED run has no [CountryTaskKind.FlagCountry] at all: the answer is then owed in
     * the learner's OWN language, so a flag alone asks them to recognize their own flag and
     * write down a name they have said all their life. Rung 7, whose whole novelty that is,
     * simply repeats the pool below it there — the same nothing an unauthored tier costs.
     *
     * The OTHER country questions keep their flag in reverse; it is merely held back while
     * the answer is owed, which is [CountryDrillTask.emojiIsGiveaway]'s business rather than
     * this list's.
     */
    fun kinds(level: Int, reverse: Boolean = false): List<CountryTaskKind> = when (level.coerceIn(1, MAX_LEVEL)) {
        1 -> listOf(CountryTaskKind.CountryName)
        2 -> listOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName)
        3, 4 -> listOf(
            CountryTaskKind.CountryName,
            CountryTaskKind.LanguageName,
            CountryTaskKind.Nationality,
        )
        5, 6 -> listOf(
            CountryTaskKind.CountryName,
            CountryTaskKind.LanguageName,
            CountryTaskKind.Nationality,
            CountryTaskKind.SpokenIn,
        )
        7, 8 -> listOfNotNull(
            CountryTaskKind.CountryName,
            CountryTaskKind.LanguageName,
            CountryTaskKind.Nationality,
            CountryTaskKind.FlagCountry.takeIf { !reverse },
            CountryTaskKind.SpokenIn,
        )
        else -> CountryTaskKind.entries.filter { !reverse || it != CountryTaskKind.FlagCountry }
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
        val kinds = kinds(level, reverse)
        var ceiling = tierCeiling(level)
        while (true) {
            val built = build(content, ceiling, kinds, reverse)
            if (built.isNotEmpty() || ceiling >= content.widestTier) return built
            ceiling++
        }
    }

    /**
     * One question from [level]'s pool, never one [solved] already holds ([DrillSolved]).
     * [avoidId] is the previous answer's id, resampled once so a repeat needs two unlucky
     * draws rather than one — the letter drill's rule. Null ⇒ the rung is answered out.
     */
    fun sample(
        content: CountryDrillContent,
        level: Int,
        reverse: Boolean,
        avoidId: String?,
        solved: Set<String>,
        rng: Random,
    ): CountryDrillTask? {
        val pool = tasks(content, level, reverse).filterNot { DrillSolved.key(it) in solved }
        if (pool.isEmpty()) return null
        var picked = pool[rng.nextInt(pool.size)]
        if (picked.id == avoidId) picked = pool[rng.nextInt(pool.size)]
        return picked
    }

    /**
     * The first rung at or above [level] with a question left ([DrillLadder.climb]). A rung
     * the run has answered out is climbed past rather than asked again — the rungs nest, so
     * the one above always has at least as much to offer.
     */
    fun draw(
        content: CountryDrillContent,
        level: Int,
        reverse: Boolean,
        avoidId: String?,
        solved: Set<String>,
        rng: Random,
    ): CountryDrillDraw {
        val climbed = DrillLadder.climb(level, MAX_LEVEL) { rung ->
            sample(content, rung, reverse, avoidId, solved, rng)
        }
        return CountryDrillDraw(climbed.task, climbed.level)
    }

    /**
     * The language an answer is owed in — the learned one, or the learner's own where the
     * run is turned round. Named here because the page that opens a run has to build the
     * grader for it before there is a run to ask.
     */
    fun answerLanguage(content: CountryDrillContent, reverse: Boolean): Language =
        if (reverse) content.source else content.target

    /** The other side of the same pair: the language the prompt is written in. */
    fun promptLanguage(content: CountryDrillContent, reverse: Boolean): Language =
        if (reverse) content.target else content.source

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
            emojiIsGiveaway = reverse,
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
            emojiIsGiveaway = reverse,
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
