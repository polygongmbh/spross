package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Language

enum class TrainerKind { Numbers, Years, Clock }

/**
 * One procedural drill task. Pure data — the UI compares typed input against
 * [accepted] normalize-insensitively and reveals [display].
 */
data class TrainerTask(
    val kind: TrainerKind,
    val language: Language,
    /**
     * The MACHINE form of the asked value: "347", "1978", "14:35" — never grouped,
     * never prettified. Callers parse it ([PhraseSlots] does `prompt.toLong()`, and a
     * Kotlin throw crossing the ObjC boundary is an app crash), so anything cosmetic
     * belongs in [promptDisplay] instead.
     */
    val prompt: String,
    /** All accepted answers, canonical reading first. */
    val accepted: List<String>,
    /** Canonical answer for the reveal. */
    val display: String,
    val gloss: String? = null,
    /**
     * What the UI shows — [prompt] with long runs of digits grouped ("4 072 918 300").
     * Defaults to [prompt], so a kind that must never be grouped stays ungrouped by
     * simply not setting it: that is why [year] and [clock] write no line for it.
     */
    val promptDisplay: String = prompt,
)

/**
 * Procedural slot trainers (numbers, years, clock times). Pure generators —
 * Kern never self-randomizes; sampling takes an injected [Random].
 * Languages come from the pack registry (de/en/es/sw/uk authored; anything
 * else is absent and the app hides the trainer hub).
 */
object Trainer {

    /** Authored trainer languages, registry order. */
    val languages: List<Language> get() = trainerPacks.keys.toList()

    fun supports(language: Language): Boolean = language in trainerPacks

    internal fun pack(language: Language): TrainerLanguagePack =
        requireNotNull(trainerPacks[language]) { "no trainer authored for language \"$language\"" }

    fun number(n: Long, language: Language): TrainerTask {
        val accepted = pack(language).number(n)
        return numberTask(n, language, accepted)
    }

    /**
     * [number] with the looser drill accepted set
     * ([TrainerLanguagePack.drillNumber]: sw adds the "na"-less spelling) —
     * shared by the level drills and the phrase slots.
     */
    internal fun drillNumber(n: Long, language: Language): TrainerTask {
        val accepted = pack(language).drillNumber(n)
        return numberTask(n, language, accepted)
    }

    /** The only place a cardinal prompt is built, so grouping cannot be forgotten on one path. */
    private fun numberTask(n: Long, language: Language, accepted: List<String>): TrainerTask {
        val prompt = n.toString()
        return TrainerTask(
            TrainerKind.Numbers, language, prompt, accepted, accepted[0],
            promptDisplay = groupDigits(prompt),
        )
    }

    /** de: hundred-style variants; sw/uk: plain number reading. */
    fun year(y: Long, language: Language): TrainerTask {
        val reading = pack(language).year(y)
        return TrainerTask(TrainerKind.Years, language, y.toString(), reading.accepted, reading.display)
    }

    /**
     * [hour]/[minute] are normalized into range; the minute is taken exactly
     * (any 0..59 — the language clocks spell or read out non-round minutes).
     */
    fun clock(hour: Int, minute: Int, language: Language): TrainerTask {
        val h = ((hour % 24) + 24) % 24
        val m = ((minute % 60) + 60) % 60
        val reading = pack(language).clock(h, m)
        return TrainerTask(
            TrainerKind.Clock, language, "${pad2(h)}:${pad2(m)}",
            reading.accepted, reading.display, reading.gloss,
        )
    }

    /**
     * Deterministic sampling with an injected RNG. Biases ported from the
     * prototype: numbers favor 2–3 digits, years cluster around 1950–2050
     * with rarer historic outliers, clock uses any hour and any minute.
     */
    fun sample(kind: TrainerKind, language: Language, rng: Random): TrainerTask = when (kind) {
        TrainerKind.Numbers -> {
            val r = rng.nextDouble()
            val n = when {
                r < 0.35 -> rng.nextLong(10, 100)
                r < 0.75 -> rng.nextLong(100, 1_000)
                else -> rng.nextLong(1_000, 10_000)
            }
            number(n, language)
        }
        TrainerKind.Years -> {
            val r = rng.nextDouble()
            val y = when {
                r < 0.55 -> rng.nextLong(1950, 2051)
                r < 0.85 -> rng.nextLong(1700, 2201)
                else -> rng.nextLong(1000, 2200)
            }
            year(y, language)
        }
        TrainerKind.Clock -> clock(rng.nextInt(24), rng.nextInt(60), language)
    }

    /**
     * Adaptive difficulty ceiling per kind. Levels are 1-based; the app ramps
     * up after consecutive successes and steps down on a miss.
     */
    fun maxLevel(kind: TrainerKind): Int = when (kind) {
        TrainerKind.Numbers -> 10 // level == digit count (up to billions)
        TrainerKind.Years -> 3
        TrainerKind.Clock -> 4
    }

    /**
     * Level semantics:
     * - numbers: level = digit count (1 → 0–9 … 10 → 1000000000–9999999999).
     * - years: 1 recent decades (1990–2029), 2 modern century (1900–2099),
     *   3 full historic range (1100–2099, German hundred-style variants).
     * - clock: 1 full hours, 2 quarters, 3 five-minute steps up to :30,
     *   4 any minute (incl. the >30 to-the-hour forms).
     */
    fun sample(kind: TrainerKind, language: Language, level: Int, rng: Random): TrainerTask {
        val l = level.coerceIn(1, maxLevel(kind))
        return when (kind) {
            TrainerKind.Numbers -> drillNumber(drawNumber(l, rng), language)
            TrainerKind.Years -> {
                val y = when (l) {
                    1 -> rng.nextLong(1990, 2030)
                    2 -> rng.nextLong(1900, 2100)
                    else -> rng.nextLong(1100, 2100)
                }
                year(y, language)
            }
            TrainerKind.Clock -> clock(rng.nextInt(24), clockMinute(l, rng), language)
        }
    }

    /** Leveled minute draw, shared by the plain clock drill and the phrase slots. */
    internal fun clockMinute(level: Int, rng: Random): Int =
        when (level.coerceIn(1, maxLevel(TrainerKind.Clock))) {
            1 -> 0
            2 -> intArrayOf(0, 15, 30, 45)[rng.nextInt(4)]
            3 -> rng.nextInt(31)
            else -> rng.nextInt(60)
        }

    /**
     * Highest place-value word for a number of the given digit count, shown
     * the first time the drill reaches a new length ("hundert", "tausend",
     * "Million" · "mia", "elfu", "milioni"). null for a single digit, beyond
     * the supported 10-digit range, or an unauthored language.
     */
    fun placeValueHint(digits: Int, language: Language): String? {
        if (digits !in 2..10) return null
        return trainerPacks[language]?.placeValues?.get(digits - 2)
    }

    /**
     * Tens look-up ("10 kumi" … "90 tisini") — Swahili only, whose tens are
     * the hardest part to recall. null for languages with regular tens.
     */
    fun tensReference(language: Language): List<String>? = trainerPacks[language]?.tensReference

    /**
     * How LONG a rung is. Two clean wins per level is the climb; fast mode spends
     * one, which is the reward for having topped the ladder the hard way.
     * (Same shape as [LetterDrill.winsToAdvance], whose pacing rule this follows.)
     */
    fun winsToAdvance(fast: Boolean): Int = if (fast) 1 else 2

    /**
     * Digits↔words inversion: the reading becomes the prompt and the value becomes
     * the answer. The app stays direction-agnostic — it always renders [TrainerTask.prompt]
     * and grades against [TrainerTask.accepted], so nothing downstream knows the direction.
     *
     * A phrase task keeps only its bare slot value as the answer, never the sentence:
     * reversing "Tuna sahani mia tatu…" asks for 347, not for the German sentence.
     */
    fun reversed(task: TrainerTask): TrainerTask {
        val value = slotValue(task)
        val accepted = when (task.kind) {
            // why: the forward prompt showed "12 345", so the separator must grade.
            TrainerKind.Numbers -> listOf(value, groupDigits(value)).distinct()
            TrainerKind.Years -> listOf(value)
            TrainerKind.Clock -> clockDigitForms(value)
            // Forms lands with its own readings (plan stage 10); its arm belongs here,
            // accepting the rendered form and — for decimals — both marks.
        }
        // The reveal shows the readable rendering, which is always one of the accepted ones.
        val reveal = if (task.kind == TrainerKind.Numbers) groupDigits(value) else value
        return TrainerTask(
            kind = task.kind, language = task.language,
            prompt = task.display, accepted = accepted,
            display = reveal, gloss = task.gloss,
        )
    }

    /**
     * The bare value a task asks about: a plain drill's whole prompt, and the digit
     * run embedded in a phrase task's sentence ("Wir haben 347 Teller." → "347").
     */
    private fun slotValue(task: TrainerTask): String =
        SLOT_VALUE.find(task.prompt)?.value ?: task.prompt

    /** "08:05" and "8:05" — the same pair the phrase slots grade against. */
    internal fun clockDigitForms(time: String): List<String> {
        val bare = time.substringBefore(':').toInt().toString() + ":" + time.substringAfter(':')
        return listOf(time, bare).distinct()
    }

    private val SLOT_VALUE = Regex("""\d+(?::\d+)?""")

    /**
     * Level-sized number with zeros biased to ~40% on the non-leading digits,
     * so the drill favours rounder values (less tedious than typing arbitrary
     * long numbers). The leading digit stays 1–9 so the value keeps exactly
     * [digits] digits.
     */
    private fun drawNumber(digits: Int, rng: Random): Long {
        if (digits <= 1) return rng.nextLong(0, 10)
        var value = rng.nextLong(1, 10)
        repeat(digits - 1) {
            val d = if (rng.nextInt(10) < 4) 0L else rng.nextLong(1, 10)
            value = value * 10 + d
        }
        return value
    }
}

internal fun pad2(value: Int): String = value.toString().padStart(2, '0')
