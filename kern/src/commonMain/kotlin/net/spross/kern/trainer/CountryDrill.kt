package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.model.Language

/**
 * The atlas drill: name the country, the people, the language — and say which is spoken
 * where. Typed answers only, in both directions.
 *
 * Registry-by-file like the letter drill: a pair has this drill exactly when
 * [net.spross.kern.catalog.Catalog.countryDrillContent] joins something for it. No
 * [NumbersReading], no [NumbersExercise] — a different skill, not another way of playing numbers.
 *
 * Everything here is pure and stateless: no schedule is read, no review is booked. Sampling
 * takes an injected [Random], so both platforms derive the same run from the same seed and
 * the ladder is pinned in tests rather than described twice in two UI layers.
 *
 * The ladder widens OUTWARD from the learner's own two languages, each Sprosse keeping
 * everything below it — and each Sprosse brings exactly ONE new thing, either a question or a
 * tier, never both at once, so that a learner who slips can say what got harder:
 *
 * | Sprosse | pool | asks |
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
 * with the ceiling, so an empty new tier simply repeats the pool below it. Sprosse 7 is that
 * same nothing in a REVERSED run, where the flag question does not exist — see [kinds].
 * What a question looks like — prompt, accepted set, display — is [CountryDrillTasks]'.
 */
object CountryDrill {
    const val MAX_SPROSSE = 9

    /** Where a pair's atlas ladder is filed: one per pair, since both languages shape its questions. */
    fun storageKey(source: Language, target: Language): String = "countries.$source-$target"

    /** Three clean wins a Sprosse ([DrillRamp.USUAL_WINS]): more Sprossen, and more rows standing on each of them. */
    const val WINS_TO_ADVANCE = DrillRamp.USUAL_WINS

    /**
     * How LONG a Sprosse is. Fast spends one clean win instead of the three, and is the reward
     * for having topped the ladder the hard way ([fastUnlocked]) — the numbers drill's rule
     * ([Numbers.winsToAdvance]), read off this ladder's own pacing.
     */
    fun winsToAdvance(fast: Boolean): Int = if (fast) 1 else WINS_TO_ADVANCE

    /**
     * Whether the Fast modifier is on offer at all. Having EVER stood on the top Sprosse is the
     * price — [bestSprosse] is the highest Sprosse any run reached, which is what the app keeps.
     */
    fun fastUnlocked(bestSprosse: Int): Boolean = bestSprosse >= FAST_PRICE

    /** The Sprosse that earns Fast, as the locked switch prices it: the top one. */
    const val FAST_PRICE: Int = MAX_SPROSSE

    /** The Sprosse ramp, on the ladder's Sprosse length ([DrillRamp.step]). */
    fun step(
        sprosse: Int,
        winsAtSprosse: Int,
        correct: Boolean,
        clean: Boolean,
        fast: Boolean = false,
    ): DrillRamp.SprosseStep =
        DrillRamp.step(sprosse, winsAtSprosse, correct, clean, winsToAdvance(fast))

    /** How far out [sprosse] reaches — tier 1 is the profile's own, 4 the regional rest. */
    fun tierCeiling(sprosse: Int): Int = when (sprosse.coerceIn(1, MAX_SPROSSE)) {
        1, 2, 3 -> 1
        4, 5 -> 2
        6, 7 -> 3
        else -> 4
    }

    /**
     * What [sprosse] may ask, in ladder order.
     *
     * A REVERSED run has no [CountryTaskKind.FlagCountry] at all: the answer is then owed in
     * the learner's OWN language, so a flag alone asks them to recognize their own flag and
     * write down a name they have said all their life. Sprosse 7, whose whole novelty that is,
     * simply repeats the pool below it there — the same nothing an unauthored tier costs.
     *
     * The OTHER country questions keep their flag in reverse; it is merely held back while
     * the answer is owed, which is [CountryDrillTask.emojiIsGiveaway]'s business rather than
     * this list's.
     */
    fun kinds(sprosse: Int, reverse: Boolean = false): List<CountryTaskKind> = when (sprosse.coerceIn(1, MAX_SPROSSE)) {
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
     * Whether [sprosse] adds NOTHING to the Sprosse below it — the same questions over the same
     * tier. Only the flag Sprosse of a reversed run is that today ([kinds]); an overview
     * reads it to say so on the row rather than promise a question the run never asks.
     */
    fun repeatsBelow(sprosse: Int, reverse: Boolean): Boolean =
        sprosse > 1 && kinds(sprosse, reverse) == kinds(sprosse - 1, reverse) &&
            tierCeiling(sprosse) == tierCeiling(sprosse - 1)

    /**
     * Every question [sprosse] could ask, in a stable order — the Sprosse's pool, made explicit.
     * [reverse] flips which side prompts: forward asks in the language the learner KNOWS,
     * reversed asks in the one they are learning and grades in their own.
     *
     * Where the ceiling's pool builds nothing at all — a catalog whose inner tiers are not
     * authored yet — it widens until something stands, because a Sprosse with no question is
     * not a Sprosse the learner can climb off.
     */
    fun tasks(content: CountryDrillContent, sprosse: Int, reverse: Boolean = false): List<CountryDrillTask> {
        val kinds = kinds(sprosse, reverse)
        var ceiling = tierCeiling(sprosse)
        while (true) {
            val built = CountryDrillTasks.build(content, ceiling, kinds, reverse)
            if (built.isNotEmpty() || ceiling >= content.widestTier) return built
            ceiling++
        }
    }

    /**
     * One question from [sprosse]'s pool, never one [solved] already holds ([DrillSolved]).
     * [avoidId] is the previous answer's id, resampled once so a repeat needs two unlucky
     * draws rather than one — the letter drill's rule. Null ⇒ the Sprosse is answered out.
     */
    fun sample(
        content: CountryDrillContent,
        sprosse: Int,
        reverse: Boolean,
        avoidId: String?,
        solved: Set<String>,
        rng: Random,
        arriving: Boolean = false,
    ): CountryDrillTask? {
        val pool = tasks(content, sprosse, reverse).filterNot { DrillSolved.key(it) in solved }
        if (pool.isEmpty()) return null
        val drawn = if (DrillLadder.leadsWithAdded(arriving, rng)) added(content, sprosse, reverse, pool) else pool
        return DrillLadder.pickAvoiding(drawn, rng) { it.id == avoidId }
    }

    /**
     * What [sprosse] ADDED — the questions in its pool that the Sprosse below could not ask, by
     * the kind it introduced or the tier it opened. Falls back to [pool] where the Sprosse adds
     * nothing ([repeatsBelow]) or where everything it added is answered out already.
     *
     * Read as a DIFFERENCE of the two pools rather than from the kind and tier separately: the
     * Sprossen widen on both axes at once, and a task is new if either of them made it so.
     */
    private fun added(
        content: CountryDrillContent,
        sprosse: Int,
        reverse: Boolean,
        pool: List<CountryDrillTask>,
    ): List<CountryDrillTask> {
        if (sprosse <= 1 || repeatsBelow(sprosse, reverse)) return pool
        val below = tasks(content, sprosse - 1, reverse).mapTo(mutableSetOf()) { DrillSolved.key(it) }
        return pool.filterNot { DrillSolved.key(it) in below }.ifEmpty { pool }
    }

    /**
     * The first Sprosse at or above [sprosse] with a question left ([DrillLadder.climb]). A Sprosse
     * the run has answered out is climbed past rather than asked again — the Sprossen nest, so
     * the one above always has at least as much to offer.
     */
    fun draw(
        content: CountryDrillContent,
        sprosse: Int,
        reverse: Boolean,
        avoidId: String?,
        solved: Set<String>,
        rng: Random,
        arriving: Boolean = false,
    ): CountryDrillDraw {
        val climbed = DrillLadder.climb(sprosse, MAX_SPROSSE) { at ->
            // Climbing PAST a spent Sprosse arrives at the one above it just as a promotion does.
            sample(content, at, reverse, avoidId, solved, rng, arriving || at > sprosse)
        }
        return CountryDrillDraw(climbed.task, climbed.sprosse)
    }

    /**
     * The Sprossen a run answered out ([DrillSolved.cleared]). Every atlas Sprosse enumerates,
     * and they nest, so answering one out answers out everything below it too.
     */
    fun cleared(content: CountryDrillContent, reverse: Boolean, solved: Set<String>): Set<Int> =
        DrillSolved.cleared(solved, MAX_SPROSSE) { sprosse ->
            tasks(content, sprosse, reverse).map { DrillSolved.key(it) }
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
}
