package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Language

/**
 * Appended, never reordered — nothing serializes the ordinal, but the app switches on it.
 * [Fraction] is a slot kind a FRAME takes, not a drill of its own: a fraction reads as a
 * bare noun, so a sentence can carry one where a whole number form cannot.
 */
enum class TrainerKind { Numbers, Years, Clock, Forms, Fraction }

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
    /**
     * Which of the number forms this task asks, as a stable key ("negative", "decimal",
     * "percent", "multiplicative", "fraction", "ordinal"); null for every other kind.
     *
     * A key, not a word: kern names the rule and the app names it in the reader's own
     * language. It exists so the first sight of a form can be introduced the way a new
     * digit length is ([placeValueHint]) — without the app reading the mark back off the
     * prompt string, which would put the notation rule in a view.
     */
    val formKey: String? = null,
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
     * The value a fraction slot asks about, read as a bare noun ("ein Viertel") —
     * frames only, which is why nothing here samples it standalone.
     */
    internal fun fraction(numerator: Long, denominator: Long, language: Language): TrainerTask {
        val value = NumberValue.Fraction(numerator, denominator)
        val accepted = pack(language).formReading(value)
        require(accepted.isNotEmpty()) { "no fraction reading for $numerator/$denominator in \"$language\"" }
        return TrainerTask(
            TrainerKind.Fraction, language,
            renderForm(value, pack(language).decimalMark, grouped = false), accepted, accepted[0],
        )
    }

    /**
     * Deterministic sampling with an injected RNG. Biases ported from the
     * prototype: numbers favor 2–3 digits, years cluster around 1950–2050
     * with rarer historic outliers, clock uses any hour and any minute.
     */
    fun sample(kind: TrainerKind, language: Language, rng: Random): TrainerTask {
        // Forms has no full-difficulty bias of its own: its ceiling IS its top rung.
        if (kind == TrainerKind.Forms) return sample(kind, language, maxLevel(kind), rng)
        // why: the full-difficulty cardinal keeps the STRICT accepted set — the looser
        // drill spellings belong to the leveled draw, which is what the drills run on.
        return render(drawSlot(kind, language, rng), language, drill = false)
    }

    /** Whether the Forms drill has anything to offer in [language] — the app's chip gate. */
    fun supportsForms(language: Language): Boolean =
        trainerPacks[language]?.formLimits?.forms?.isNotEmpty() == true

    /**
     * Adaptive difficulty ceiling per kind. Levels are 1-based; the app ramps
     * up after consecutive successes and steps down on a miss.
     */
    fun maxLevel(kind: TrainerKind): Int = when (kind) {
        TrainerKind.Numbers -> 10 // level == digit count (up to billions)
        TrainerKind.Years -> 3
        TrainerKind.Clock -> CLOCK_MAX_LEVEL
        TrainerKind.Forms -> FORMS_MAX_LEVEL
        TrainerKind.Fraction -> FRACTION_MAX_LEVEL
    }

    /**
     * Whether [language] can fill this slot kind at all. A cardinal, a year and a clock
     * come with every pack; a fraction needs the pack to READ one, so a frame taking that
     * slot simply never joins where it cannot be answered — the registry rule again.
     */
    fun supportsSlot(kind: TrainerKind, language: Language): Boolean {
        val pack = trainerPacks[language] ?: return false
        if (kind != TrainerKind.Fraction) return kind != TrainerKind.Forms
        return NumberForm.Fraction in pack.formLimits.forms &&
            pack.formLimits.fractionDenominators.any { it >= 3 }
    }

    /**
     * Level semantics:
     * - numbers: level = digit count (1 → 0–9 … 10 → 1000000000–9999999999).
     * - years: 1 recent decades (1990–2029), 2 modern century (1900–2099),
     *   3 full historic range (1100–2099, German hundred-style variants).
     * - clock: the five nested rungs of [clockRung] — 1 full hours, 2 the quarters,
     *   3 five-minute steps to the half (:45 kept), 4 the whole five-minute grid
     *   (the to-the-hour countdown), 5 any minute.
     * - forms: the ten rungs of [rungForms], each keeping everything below it.
     */
    fun sample(kind: TrainerKind, language: Language, level: Int, rng: Random): TrainerTask {
        val l = level.coerceIn(1, maxLevel(kind))
        if (kind == TrainerKind.Forms) return formTask(language, l, 0, rng)
        return render(drawSlot(kind, language, l, rng), language, drill = true)
    }

    /**
     * The one place a drawn [SlotValue] becomes a task, so the drills and the phrase slots
     * can never render the same draw two different ways. [drill] picks the looser accepted
     * set the level drills grade against (sw's "na"-less spelling).
     */
    private fun render(value: SlotValue, language: Language, drill: Boolean): TrainerTask =
        when (value) {
            is SlotValue.Count -> if (drill) drillNumber(value.n, language) else number(value.n, language)
            is SlotValue.Year -> year(value.y, language)
            is SlotValue.Time -> clock(value.hour, value.minute, language)
            is SlotValue.Part -> fraction(value.numerator, value.denominator, language)
        }

    /**
     * A Forms task whose values are sized by a NUMBERS rung rather than by the forms
     * ladder's own gentler one — [DrillModifier.Mix]'s second half, where "−7" grows into
     * "−4 072 918" and "3,7" into "12 345,7". [level] still decides which forms are on
     * offer; [magnitudeDigits] only widens the two that have a magnitude to widen.
     */
    fun sampleForms(language: Language, level: Int, magnitudeDigits: Int, rng: Random): TrainerTask =
        formTask(language, level.coerceIn(1, FORMS_MAX_LEVEL), magnitudeDigits.coerceIn(0, 10), rng)

    /**
     * One number-form task: the ladder draws the value, the pack reads it, and the
     * prompt is rendered with that pack's decimal mark (the one language-dependent
     * prompt in the trainer — see [renderForm]).
     */
    private fun formTask(language: Language, level: Int, magnitudeDigits: Int, rng: Random): TrainerTask {
        val pack = pack(language)
        val value = drawForm(pack.formLimits, level, rng, magnitudeDigits)
        val accepted = value?.let(pack::formReading).orEmpty()
        // why: a pack that reads no form still has to answer sample(Forms, …) — it falls
        // back to a plain cardinal rather than throwing across the ObjC boundary. The app
        // never shows it: the Forms variant is gated on supportsForms().
        if (value == null || accepted.isEmpty()) {
            return drillNumber(drawNumber(level, rng), language).copy(kind = TrainerKind.Forms)
        }
        val prompt = renderForm(value, pack.decimalMark, grouped = false)
        return TrainerTask(
            TrainerKind.Forms, language, prompt, accepted, accepted[0],
            promptDisplay = renderForm(value, pack.decimalMark, grouped = true),
            formKey = value.form.key,
        )
    }

    /** Leveled minute draw, shared by the plain clock drill and the phrase slots. */
    internal fun clockMinute(level: Int, rng: Random): Int = drawClockMinute(level, rng)

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
     * The word this language adds for a number form ("minus", "Komma", "por ciento"),
     * shown the first time a run asks that form — the [placeValueHint] rule, for marks
     * instead of lengths. Keyed as [TrainerTask.formKey] names it; null for an unknown
     * key or a form this language does not read.
     */
    fun formHint(formKey: String, language: Language): String? =
        NumberForm.entries.firstOrNull { it.key == formKey }?.let { formMarker(language, it) }

    /**
     * The whole numbers page for a language: which values a reference shows and how
     * this language reads them. Generated from the same packs the drill grades against,
     * so the table can never drift from the answers.
     *
     * Sections carry a stable [ReferenceSection.key] the app localizes into a heading.
     */
    fun reference(language: Language): List<ReferenceSection> = buildReference(language)

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
            // why: a form is written, not just spelled — "3,7" and "3.7" are the same
            // number, "20." and "20" the same rank, so the notation must not cost the rung.
            TrainerKind.Forms -> formDigitForms(task.prompt, task.promptDisplay)
            // A fraction has one notation and no separator to get wrong.
            TrainerKind.Fraction -> listOf(value)
        }
        // The reveal shows the readable rendering, which is always one of the accepted ones.
        val reveal = when (task.kind) {
            TrainerKind.Numbers -> groupDigits(value)
            TrainerKind.Forms -> task.promptDisplay
            else -> value
        }
        return TrainerTask(
            kind = task.kind, language = task.language,
            prompt = task.display, accepted = accepted,
            display = reveal, gloss = task.gloss,
        )
    }

    /**
     * The bare value a task asks about: a plain drill's whole prompt, and the value
     * embedded in a phrase task's sentence ("Wir haben 347 Teller." → "347",
     * "Ich brauche 1/4 Kilo Mehl." → "1/4").
     */
    private fun slotValue(task: TrainerTask): String =
        SLOT_VALUE.find(task.prompt)?.value ?: task.prompt

    /** "08:05" and "8:05" — the same pair the phrase slots grade against. */
    internal fun clockDigitForms(time: String): List<String> {
        val bare = time.substringBefore(':').toInt().toString() + ":" + time.substringAfter(':')
        return listOf(time, bare).distinct()
    }

    /** A clock time, a fraction, or a plain run of digits — whichever the sentence carries. */
    private val SLOT_VALUE = Regex("""\d+(?:[:/]\d+)?""")
}

internal fun pad2(value: Int): String = value.toString().padStart(2, '0')
