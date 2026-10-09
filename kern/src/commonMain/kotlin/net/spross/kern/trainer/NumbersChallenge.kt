package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Language
import net.spross.kern.session.TurnFeedback

/**
 * A timed numbers run two learners play on the same questions, shared as a short code
 * (`K4F7-2Q7M`): eight Crockford characters carrying the seed, the language, the picks,
 * the sender's score on a reply, and a check.
 *
 * The seed spells the whole question list, since kern's [Random] draws the same on every platform.
 * Unlike a ramp it is a fixed SCRIPT ([tasks]): question k is at Sprosse `1 + k / 2` regardless
 * of earlier answers, so both players' questions stay identical.
 *
 * Only exercises independent of the source language travel; Phrases does not.
 * The check covers the spelled questions too, so a mistyped code or one from a differently
 * drawing app is refused.
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

    /** The run, every question taken from the script ([drawAt]). */
    fun open(): NumbersRunState {
        val opening = drawAt(0, emptyMap())
        return NumbersRunState(
            mode = mode,
            // why: a script is never empty — its first draw is at Sprosse 1 with nothing solved.
            current = requireNotNull(opening.drawn) { "empty challenge ${code(null)}" },
            index = 0,
            sprossen = opening.sprossen,
            winsAtSprosse = emptyMap(),
            bestSprossen = emptyMap(),
            core = DrillRunCore(),
            seenDigitCounts = emptySet(),
            seenFormKeys = emptySet(),
            hintUsed = false,
            feedback = TurnFeedback.Neutral,
            finished = false,
            challenge = this,
        )
    }

    /** The code as the sender shares it; with [score], the code a reply carries. */
    fun code(score: Int?): String {
        val body = body(score)
        val chars = base32((body shl CHECK_BITS) or check(body), CODE_CHARS)
        return "${chars.substring(0, CODE_CHARS / 2)}-${chars.substring(CODE_CHARS / 2)}"
    }

    /** [code] as a link that opens the app on it, or the site's challenge page where none is installed. */
    fun link(score: Int?): String = "$LINK_PREFIX${code(score)}"

    /** Question [index] as a draw at its Sprosse; a null task past the end ends the run. */
    internal fun drawAt(index: Int, sprossen: Map<NumbersExercise, Int>): NumbersDraw {
        val next = tasks.getOrNull(index) ?: return NumbersDraw(null, sprossen)
        return NumbersDraw(next.drawn, sprossen + (next.drawn.exercise to next.sprosse))
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
            out += ChallengeTask(drawn, draw.sprossen.getValue(drawn.exercise))
            solved += DrillSolved.key(drawn.exercise, drawn.task)
            avoiding = drawn.task.prompt
        }
        return out
    }

    /** Everything but the check, high bits first: seed, language, picks, score. */
    private fun body(score: Int?): Long {
        val scoreField = score?.let { minOf(it, MAX_SCORE) + 1 } ?: 0
        return (seed.toLong() shl (LANGUAGE_BITS + SPEC_BITS + SCORE_BITS)) or
            (CODE_LANGUAGES.indexOf(language).toLong() shl (SPEC_BITS + SCORE_BITS)) or
            (specBits().toLong() shl SCORE_BITS) or
            scoreField.toLong()
    }

    private fun specBits(): Int =
        exercises.sumOf { 1 shl TRAVELING.indexOf(it) } +
            (if (reverse) REVERSE_BIT else 0) +
            (if (mix) MIX_BIT else 0)

    /** Over the code's own bits AND the questions they spell ([NumbersChallenge]). */
    private fun check(body: Long): Long {
        val questions = tasks.joinToString("|") { "${it.drawn.task.prompt}=${it.drawn.task.display}" }
        return (fnv1a("$body|$questions") and CHECK_MASK).toLong()
    }

    companion object {
        /** Questions a script holds — more than a run with its earned seconds can answer. */
        const val LENGTH: Int = 150

        // The 40 bits of eight characters: 14 + 5 + 5 + 10 + 6.
        private const val CODE_CHARS = 8
        private const val SEED_BITS = 14
        private const val LANGUAGE_BITS = 5
        private const val SPEC_BITS = 5
        private const val SCORE_BITS = 10
        private const val CHECK_BITS = 6
        private const val CHECK_MASK = (1 shl CHECK_BITS) - 1
        /** The highest score a reply carries; 0 in the field means none. */
        private const val MAX_SCORE = (1 shl SCORE_BITS) - 2
        private const val REVERSE_BIT = 8
        private const val MIX_BIT = 16

        /** The languages a code can name, by index: append a new one, never insert or reorder. */
        private val CODE_LANGUAGES = listOf("de", "en", "eo", "es", "fr", "it", "sw", "uk")

        /** Crockford's base32: no I, L, O or U, so a code read aloud cannot be misheard. */
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        /** The exercises a code can carry, in the order its bits name them. */
        private val TRAVELING = listOf(NumbersExercise.Counting, NumbersExercise.Clock, NumbersExercise.Forms)

        private const val LINK_PREFIX = "https://spross.net/c?"
        private const val SCHEME_PREFIX = "spross://challenge/"

        /** The code a challenge link carries — the site's or the app scheme's — for [read]; null for any other URL. */
        fun codeInLink(url: String): String? =
            listOf(LINK_PREFIX, SCHEME_PREFIX)
                .firstOrNull { url.startsWith(it, ignoreCase = true) }
                ?.let { url.substring(it.length).substringBefore('#') }
                ?.takeIf { it.isNotBlank() }

        /** Whether [create] has anything to send out of these picks. */
        fun offered(mode: NumbersMode): Boolean =
            mode.language in CODE_LANGUAGES && mode.exercises.any { it in TRAVELING }

        /** A fresh challenge from the page's picks, minus Phrases; null if nothing is left. */
        fun create(mode: NumbersMode, rng: Random): NumbersChallenge? {
            if (!offered(mode)) return null
            val exercises = mode.exercises.filter { it in TRAVELING }
            return NumbersChallenge(
                language = mode.language,
                exercises = exercises,
                reverse = DrillModifier.Reverse in mode.modifiers,
                mix = DrillModifier.Mix in mode.modifiers,
                seed = rng.nextInt(1 shl SEED_BITS),
                opponentScore = null,
            )
        }

        /** Parses a typed code for [language], forgiving case, spaces, dashes, O for 0 and I/L for 1. */
        fun read(text: String, language: Language): ChallengeReading {
            val chars = text.uppercase().filter { it.isLetterOrDigit() }.map(::crockford)
            if (chars.size != CODE_CHARS || chars.any { it < 0 }) return ChallengeReading.Unreadable
            val all = chars.fold(0L) { acc, v -> acc * 32 + v }
            val body = all shr CHECK_BITS
            fun field(shift: Int, bits: Int) = ((body shr shift) and ((1L shl bits) - 1)).toInt()
            val scoreField = field(0, SCORE_BITS)
            val spec = field(SCORE_BITS, SPEC_BITS)
            val codeLanguage = CODE_LANGUAGES.getOrNull(field(SCORE_BITS + SPEC_BITS, LANGUAGE_BITS))
            if (codeLanguage == null || !Numbers.supports(codeLanguage)) return ChallengeReading.Unreadable
            val exercises = TRAVELING.filterIndexed { bit, _ -> spec and (1 shl bit) != 0 }
            val offered = DrillSelection.offered(codeLanguage, phrasesRealized = false)
            if (exercises.isEmpty() || !offered.containsAll(exercises)) return ChallengeReading.Unreadable
            val challenge = NumbersChallenge(
                language = codeLanguage,
                exercises = exercises,
                reverse = spec and REVERSE_BIT != 0,
                mix = spec and MIX_BIT != 0,
                seed = field(SCORE_BITS + SPEC_BITS + LANGUAGE_BITS, SEED_BITS),
                opponentScore = (scoreField - 1).takeIf { scoreField > 0 },
            )
            if (challenge.check(body) != (all and CHECK_MASK.toLong())) return ChallengeReading.Unreadable
            if (codeLanguage != language) return ChallengeReading.OtherLanguage(codeLanguage)
            return ChallengeReading.Ready(challenge)
        }

        private fun base32(value: Long, chars: Int): String =
            (chars - 1 downTo 0).map { ALPHABET[((value shr (5 * it)) and 31).toInt()] }.joinToString("")

        private fun crockford(c: Char): Int = ALPHABET.indexOf(canonical(c))

        private fun canonical(c: Char): Char = when (c) {
            'O' -> '0'
            'I', 'L' -> '1'
            else -> c
        }

        /** FNV-1a over UTF-8: stable across platforms, unlike [String.hashCode]. */
        private fun fnv1a(text: String): Int =
            text.encodeToByteArray().fold(FNV_OFFSET) { hash, byte ->
                (hash xor (byte.toInt() and 0xFF)) * FNV_PRIME
            } and Int.MAX_VALUE

        private const val FNV_OFFSET = -0x7ee3623b
        private const val FNV_PRIME = 0x01000193
    }
}

/** One question of a challenge's script and the Sprosse it is scored at. */
data class ChallengeTask(val drawn: DrawnTask, val sprosse: Int)

/** What a typed code turned out to be. */
sealed class ChallengeReading {
    data class Ready(val challenge: NumbersChallenge) : ChallengeReading()

    /** A valid code for another learned language. */
    data class OtherLanguage(val language: Language) : ChallengeReading()

    /** Mistyped, cut short, or made by an app that would draw other questions from it. */
    data object Unreadable : ChallengeReading()
}
