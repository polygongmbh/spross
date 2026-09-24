package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Language
import net.spross.kern.session.TurnFeedback

/**
 * A timed numbers run two learners can play on the SAME questions, carried from one phone to
 * the other as a short code (`ES-K4F7-2Q7M`, optionally `-42` with the sender's score).
 *
 * No server: one seed spells the whole question list, and kern's [Random] draws the same
 * values on every platform. What the run cannot be is a ramp — a ramp answers the learner,
 * so two players' questions would part at the first miss. A challenge is a SCRIPT instead
 * ([tasks]): question k is drawn at Sprosse `1 + k / 2`, whatever was answered before it,
 * and scored like any timed run ([TimedRun.points]).
 *
 * Only exercises whose prompt never depends on the SOURCE language travel — digits one way,
 * the learned language's own reading the other — so a German and an English speaker who both
 * learn Spanish meet the same card. Phrases, prompted in the source, does not.
 *
 * The code's check is taken over the questions it spells, not only over its own letters, so
 * a code typed wrong and a code made by an app that draws differently are refused alike —
 * a challenge never silently hands two players two different runs.
 */
data class NumbersChallenge(
    val language: Language,
    /** Never empty, never Phrases, in ladder order. */
    val exercises: List<NumbersExercise>,
    val reverse: Boolean,
    val mix: Boolean,
    /** [SEED_BITS] wide. */
    val seed: Int,
    /** What the code arrived with, where the sender played it first; null for a fresh one. */
    val opponentScore: Int?,
) {

    /** What the run is spelled out of — timed, and played the way the code says. */
    val mode: NumbersMode
        get() = NumbersMode(
            exercises,
            language,
            setOfNotNull(
                DrillModifier.Timed,
                DrillModifier.Reverse.takeIf { reverse },
                DrillModifier.Mix.takeIf { mix },
            ),
        )

    /** Every question of the run, in order, with the Sprosse it is scored at. */
    val tasks: List<ChallengeTask> by lazy { script() }

    /**
     * The run: the script's first question at the Sprosse it sets, and every later one out of
     * the script rather than the ramp's draw ([drawAt]). Nothing random is left to take.
     */
    fun open(): NumbersRunState {
        val opening = drawAt(0, emptyMap())
        return NumbersRunState(
            mode = mode,
            // why: a script is never empty — its first draw is at Sprosse 1 with nothing solved.
            current = requireNotNull(opening.drawn) { "empty challenge ${code(null)}" },
            index = 0,
            levels = opening.levels,
            winsAtLevel = emptyMap(),
            bestLevels = emptyMap(),
            core = DrillRunCore(),
            seenDigitCounts = emptySet(),
            hintUsed = false,
            feedback = TurnFeedback.Neutral,
            finished = false,
            challenge = this,
        )
    }

    /** The code as the sender shares it; with [score], the code a reply carries. */
    fun code(score: Int?): String {
        val chars = payload() + check()
        val base = "${language.uppercase()}-${chars.substring(0, 4)}-${chars.substring(4)}"
        return if (score == null) base else "$base-$score"
    }

    /**
     * Question [index] as a draw — the Sprosse the script puts it at, with the exercise that
     * asks it moved there; a null task past the end, which ends the run.
     */
    internal fun drawAt(index: Int, levels: Map<NumbersExercise, Int>): NumbersDraw {
        val next = tasks.getOrNull(index) ?: return NumbersDraw(null, levels)
        return NumbersDraw(next.drawn, levels + (next.drawn.exercise to next.level))
    }

    private fun script(): List<ChallengeTask> {
        val rng = Random(seed)
        val run = mode
        val solved = mutableSetOf<String>()
        val out = mutableListOf<ChallengeTask>()
        var avoiding: String? = null
        for (k in 0 until LENGTH) {
            val sprosse = 1 + k / Numbers.winsToAdvance(fast = false)
            val draw = run.draw(run.exercises.associateWith { sprosse }, avoiding, solved, rng)
            val drawn = draw.drawn ?: break
            out += ChallengeTask(drawn, draw.levels.getValue(drawn.exercise))
            solved += DrillSolved.key(drawn.exercise, drawn.task)
            avoiding = drawn.task.prompt
        }
        return out
    }

    private fun payload(): String =
        base32(specBits(), 1) + base32(seed, SEED_BITS / 5)

    private fun specBits(): Int =
        exercises.sumOf { 1 shl TRAVELLING.indexOf(it) } +
            (if (reverse) REVERSE_BIT else 0) +
            (if (mix) MIX_BIT else 0)

    /** Over the code's own letters AND the questions they spell ([NumbersChallenge]). */
    private fun check(): String {
        val questions = tasks.joinToString("|") { "${it.drawn.task.prompt}=${it.drawn.task.display}" }
        return base32(fnv1a("$language|${payload()}|$questions") and CHECK_MASK, CHECK_CHARS)
    }

    companion object {
        /** Questions a script holds — more than a minute can answer. */
        const val LENGTH: Int = 60

        private const val SEED_BITS = 25
        private const val CHECK_CHARS = 2
        private const val CHECK_MASK = (1 shl (5 * CHECK_CHARS)) - 1
        private const val REVERSE_BIT = 8
        private const val MIX_BIT = 16
        private const val PAYLOAD_CHARS = 1 + SEED_BITS / 5 + CHECK_CHARS
        private const val MAX_SCORE_DIGITS = 5

        /** Crockford's base32: no I, L, O or U, so a code read aloud cannot be misheard. */
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        /** The exercises a code can carry, in the order its bits name them. */
        private val TRAVELLING = listOf(NumbersExercise.Counting, NumbersExercise.Clock, NumbersExercise.Forms)

        /** Whether [create] has anything to send out of these picks. */
        fun offered(mode: NumbersMode): Boolean = mode.exercises.any { it in TRAVELLING }

        /**
         * A fresh challenge out of the run the page describes: its exercises bar Phrases, and
         * its direction. Null where nothing it picks can travel — a Phrases-only pick.
         */
        fun create(mode: NumbersMode, rng: Random): NumbersChallenge? {
            if (!offered(mode)) return null
            val exercises = mode.exercises.filter { it in TRAVELLING }
            return NumbersChallenge(
                language = mode.language,
                exercises = exercises,
                reverse = DrillModifier.Reverse in mode.modifiers,
                mix = DrillModifier.Mix in mode.modifiers,
                seed = rng.nextInt(1 shl SEED_BITS),
                opponentScore = null,
            )
        }

        /**
         * A code as a learner typed or pasted it, read for someone learning [language].
         * Case, spaces and dashes are forgiven, and so are O for 0 and I or L for 1.
         */
        fun read(text: String, language: Language): ChallengeReading {
            val plain = text.uppercase().filter { it.isLetterOrDigit() }
            if (plain.length < 2 + PAYLOAD_CHARS) return ChallengeReading.Unreadable
            val codeLanguage = plain.substring(0, 2).lowercase()
            val payload = plain.substring(2, 2 + PAYLOAD_CHARS).map(::crockford)
            val tail = plain.substring(2 + PAYLOAD_CHARS)
            if (payload.any { it < 0 } || tail.length > MAX_SCORE_DIGITS || tail.any { !it.isDigit() }) {
                return ChallengeReading.Unreadable
            }
            if (!Numbers.supports(codeLanguage)) return ChallengeReading.Unreadable
            val spec = payload[0]
            val exercises = TRAVELLING.filterIndexed { bit, _ -> spec and (1 shl bit) != 0 }
            val offered = DrillSelection.offered(codeLanguage, phrasesRealized = false)
            if (exercises.isEmpty() || !offered.containsAll(exercises)) return ChallengeReading.Unreadable
            val challenge = NumbersChallenge(
                language = codeLanguage,
                exercises = exercises,
                reverse = spec and REVERSE_BIT != 0,
                mix = spec and MIX_BIT != 0,
                seed = payload.subList(1, 1 + SEED_BITS / 5).fold(0) { acc, v -> acc * 32 + v },
                opponentScore = tail.toIntOrNull(),
            )
            val given = payload.takeLast(CHECK_CHARS).joinToString("") { ALPHABET[it].toString() }
            if (challenge.check() != given) return ChallengeReading.Unreadable
            if (codeLanguage != language) return ChallengeReading.OtherLanguage(codeLanguage)
            return ChallengeReading.Ready(challenge)
        }

        private fun base32(value: Int, chars: Int): String =
            (chars - 1 downTo 0).map { ALPHABET[(value shr (5 * it)) and 31] }.joinToString("")

        private fun crockford(c: Char): Int = ALPHABET.indexOf(canonical(c))

        private fun canonical(c: Char): Char = when (c) {
            'O' -> '0'
            'I', 'L' -> '1'
            else -> c
        }

        /** FNV-1a over UTF-8: the same number on every platform, which [String.hashCode] is not promised to be. */
        private fun fnv1a(text: String): Int =
            text.encodeToByteArray().fold(FNV_OFFSET) { hash, byte ->
                (hash xor (byte.toInt() and 0xFF)) * FNV_PRIME
            } and Int.MAX_VALUE

        private const val FNV_OFFSET = -0x7ee3623b
        private const val FNV_PRIME = 0x01000193
    }
}

/** One question of a challenge's script and the Sprosse it is scored at. */
data class ChallengeTask(val drawn: DrawnTask, val level: Int)

/** What a typed code turned out to be. */
sealed class ChallengeReading {
    data class Ready(val challenge: NumbersChallenge) : ChallengeReading()

    /** A good code for another learned language — the page names it rather than refusing blind. */
    data class OtherLanguage(val language: Language) : ChallengeReading()

    /** Mistyped, cut short, or made by an app that would draw other questions from it. */
    data object Unreadable : ChallengeReading()
}
