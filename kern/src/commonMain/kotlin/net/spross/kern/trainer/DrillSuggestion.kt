package net.spross.kern.trainer

import kotlin.time.Instant
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.Inventory
import net.spross.kern.box.partSlot
import net.spross.kern.box.zoneOf
import net.spross.kern.model.CardKind
import net.spross.kern.model.Language
import net.spross.kern.model.fnv1a64
import net.spross.kern.session.SessionOffer

/**
 * The ONE drill Home names, so the hub's six chips are a choice nobody has to make.
 *
 * Every drill the hub offers stands as a candidate unless its ladder is mastered, and each
 * scores three terms added together:
 * - **how long since it last ran**, in local days, saturating at [RECENCY_DAYS] —
 *   a drill never run scores the full term, the same as one untouched for a week;
 * - **what the box would get out of it** ([benefit]) — letters while a new script is young,
 *   numbers while the box is, the scrambles once there are grown words to scramble;
 * - **how much of its ladder is left** ([Ladder.share]), at [LADDER_WEIGHT].
 *
 * A seeded nudge of at most [NUDGE] per drill, keyed on the greeting's [partSlot], turns
 * close calls over when the day's part does and never between two renders.
 */
object DrillSuggestion {

    /** Store prefix of the last-run stamps — the full key is this plus [lastRunKey]. */
    const val LAST_RUN_PREFIX: String = "trainer.lastRun."

    /** Where [drill]'s last closed run in [language] is stamped, as epoch millis. */
    fun lastRunKey(drill: Drill, language: Language): String = "${drill.name}.$language"

    /** Local days since a run after which recency adds nothing more. */
    const val RECENCY_DAYS: Int = 7

    /** Grown words by which the box is past "early on" — numbers and a new script stop leading. */
    const val EARLY_WORDS: Int = 150

    /** Grown words at which the word scramble is worth its full term; the sentence one takes twice this. */
    const val SCRAMBLE_WORDS: Int = 150

    const val LADDER_WEIGHT: Double = 0.3
    const val NUDGE: Double = 0.25

    /** What every drill is worth to any box — the floor the steering terms rise from. */
    const val BASE_BENEFIT: Double = 0.2

    /** How far a benefit must rise over [BASE_BENEFIT] to be the reason given. */
    private const val STEER_NAMED_FROM: Double = 0.3

    /**
     * Whether Home names a drill at all: once the day has answered more cards than are still
     * due — none left is the plain case of that — and then only as what leads it ([DayLead]).
     */
    fun shown(offer: SessionOffer): Boolean = offer.doneToday > offer.dueNow

    /**
     * The drill to name, or null where every candidate's ladder is mastered or none is offered.
     * [standings] are the drills the hub offers right now, and only those.
     */
    fun suggest(
        standings: List<Standing>,
        facts: BoxFacts,
        nowEpochMillis: Long,
        tzId: String,
        language: Language?,
    ): Pick? {
        val slot = partSlot(nowEpochMillis, tzId, language)
        return standings
            .filter { it.ladder?.mastered != true }
            .map { Scored(it, daysSince(it.lastRunEpochMillis, nowEpochMillis, tzId), facts) }
            .maxWithOrNull(
                compareBy<Scored> { it.total + nudge(slot, it.standing.drill) }
                    .thenBy { -it.standing.drill.ordinal },
            )
            ?.pick()
    }

    /** What [drill] is worth to a box standing at [facts], from [BASE_BENEFIT] up to 1. */
    fun benefit(drill: Drill, facts: BoxFacts): Double {
        val early = 1.0 - fraction(facts.settledWords, EARLY_WORDS)
        val steer = when (drill) {
            Drill.Letters -> if (facts.newScript) early else 0.0
            Drill.Numbers -> 0.6 * early
            Drill.WordScramble -> 0.8 * fraction(facts.settledWords, SCRAMBLE_WORDS)
            Drill.SentenceScramble -> 0.8 * fraction(facts.settledWords, 2 * SCRAMBLE_WORDS)
            Drill.Countries, Drill.Dates -> 0.0
        }
        return maxOf(BASE_BENEFIT, steer)
    }

    /** Why a drill is named. Which words say so is the platform's. */
    enum class Reason {
        /** Letters, while the target's script is new to the learner and the box is young. */
        NewScript,

        /** Numbers, while the box is young. */
        EarlyNumbers,

        /** A scramble, once the box holds enough grown words. */
        WordsSettled,
        NeverRun,

        /** Not run for [Pick.daysSinceRun] days, at least two. */
        NotLately,

        /** Nothing stands out: the pick is for the change of pace itself. */
        Variety,
    }

    /** The named drill and why; [daysSinceRun] is -1 where it never ran. */
    data class Pick(val drill: Drill, val reason: Reason, val daysSinceRun: Int)

    /** One offered drill as the rule weighs it. */
    data class Standing(
        val drill: Drill,
        /** When a run of it last closed ([LAST_RUN_PREFIX]); null where none ever has. */
        val lastRunEpochMillis: Long?,
        /** Its ladder; null where the drill files none, which never counts as mastered. */
        val ladder: Ladder?,
    )

    /** How many of a ladder's [total] Sprossen are still [left] to clear. */
    data class Ladder(val left: Int, val total: Int) {
        val mastered: Boolean get() = total > 0 && left <= 0
        val share: Double get() = if (total <= 0) 0.5 else left.coerceIn(0, total).toDouble() / total

        companion object {
            /** A ladder that files its cleared Sprossen: the atlas, the calendar, both scrambles. */
            fun cleared(cleared: Set<Int>, top: Int): Ladder =
                Ladder((1..maxOf(1, top)).count { it !in cleared }, maxOf(1, top))

            /**
             * The numbers ladders, which file the highest Sprosse REACHED per exercise
             * ([NumbersMode.progressKey]): a Sprosse is behind the learner once they stood
             * above it. Counting, Clock and, where the pack has them, Forms — Phrases rides
             * on frames the pair may never realize, so it is no ladder to master.
             */
            fun numbers(reached: Map<NumbersExercise, Int>, language: Language): Ladder {
                val exercises = listOfNotNull(
                    NumbersExercise.Counting,
                    NumbersExercise.Clock,
                    NumbersExercise.Forms.takeIf { Numbers.supportsForms(language) },
                )
                val tops = exercises.associateWith { Numbers.maxLevel(it.reading!!) }
                val behind = exercises.sumOf { ((reached[it] ?: 0) - 1).coerceIn(0, tops.getValue(it)) }
                val total = tops.values.sum()
                return Ladder(total - behind, total)
            }
        }
    }

    /** What the box says about which drill would serve it. */
    data class BoxFacts(
        /** Single words past the display bar ([BoxEngine.hasSettled]). */
        val settledWords: Int,
        /** Whether the learned language writes in another script than the known one. */
        val newScript: Boolean,
    ) {
        companion object {
            fun of(box: BoxState): BoxFacts {
                val grown = Inventory.active(box).count {
                    box.cards[it.cardId]?.kind in singleWords && BoxEngine.hasSettled(box, it.cardId)
                }
                val pairs = Inventory.joinedCards(box).asSequence()
                    .mapNotNull { card -> script(card.target.text)?.let { it to script(card.source.text) } }
                    .filter { it.second != null }
                    .take(SCRIPT_SAMPLE)
                    .toList()
                return BoxFacts(grown, pairs.count { it.first != it.second } * 2 > pairs.size)
            }

            private const val SCRIPT_SAMPLE = 64
            private val singleWords = setOf(CardKind.Noun, CardKind.Verb, CardKind.Adjective)

            /** The Unicode block family of a text's first letter: Latin, Greek, Cyrillic, or its own. */
            private fun script(text: String): Int? {
                val letter = text.firstOrNull { it.isLetter() } ?: return null
                return when (letter.code) {
                    in 0..0x24F, in 0x1E00..0x1EFF -> 0
                    in 0x370..0x3FF -> 1
                    in 0x400..0x52F -> 2
                    else -> 0x100 + (letter.code shr 7)
                }
            }
        }
    }

    private class Scored(val standing: Standing, val days: Int?, facts: BoxFacts) {
        val recency = days?.let { fraction(it, RECENCY_DAYS) } ?: 1.0
        val benefit = benefit(standing.drill, facts)
        val total = recency + benefit + LADDER_WEIGHT * (standing.ladder?.share ?: 0.5)

        fun pick(): Pick {
            val reason = when {
                benefit - BASE_BENEFIT >= STEER_NAMED_FROM -> steeredBy(standing.drill)
                days == null -> Reason.NeverRun
                days >= 2 -> Reason.NotLately
                else -> Reason.Variety
            }
            return Pick(standing.drill, reason, days ?: -1)
        }
    }

    private fun steeredBy(drill: Drill): Reason = when (drill) {
        Drill.Letters -> Reason.NewScript
        Drill.Numbers -> Reason.EarlyNumbers
        else -> Reason.WordsSettled
    }

    private fun daysSince(then: Long?, nowEpochMillis: Long, tzId: String): Int? {
        then ?: return null
        val zone = zoneOf(tzId)
        val day = { millis: Long -> Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date }
        return maxOf(0, day(then).daysUntil(day(nowEpochMillis)))
    }

    /** The slot's nudge for [drill], in [0, NUDGE). FNV-1a, so both phones and every relaunch agree. */
    private fun nudge(slot: String, drill: Drill): Double {
        var hash = fnv1a64("$slot:${drill.name}")
        // why: FNV leaves its low bits barely mixed, and the modulo reads exactly those.
        hash = hash xor (hash shr 33)
        return (hash % 1000uL).toDouble() / 1000.0 * NUDGE
    }

    private fun fraction(count: Int, of: Int): Double = (count.toDouble() / of).coerceIn(0.0, 1.0)
}
