package net.spross.kern.trainer

import net.spross.kern.model.Language

internal data class ClockReading(
    val display: String,
    val accepted: List<String>,
    val gloss: String? = null,
)

internal data class YearReading(val display: String, val accepted: List<String>)

/**
 * Per-language generator bundle. All cardinal arithmetic is Long — Kotlin Int
 * is 32-bit on every platform (the v1 arm64_32 guard, generalized): values
 * beyond 9 999 999 999 fall back to digits inside each generator.
 */
internal interface TrainerLanguagePack {
    /** Accepted cardinal spellings, canonical first. */
    fun number(n: Long): List<String>

    fun year(y: Long): YearReading

    fun clock(hour: Int, minute: Int): ClockReading

    /** Highest place word per digit count; index 0 → 2 digits … index 8 → 10 digits. */
    val placeValues: List<String>

    /**
     * Every part of the day this clock can hang on a reading. Derived, never authored
     * twice — and abstract on purpose: a sixth pack that forgot it would otherwise get
     * no collision coverage in silence, which is the hole this member exists to close.
     */
    val clockDayParts: Set<String>

    /**
     * Accepted readings of a number form, canonical first — empty where the language
     * has no reading for it. Defaulted like [formLimits] and [decimalMark] so an
     * unauthored language quietly offers no Forms drill instead of forcing every pack
     * to change at once.
     */
    fun formReading(value: NumberValue): List<String> = emptyList()

    /** Which forms this language can be drilled on, and how far each reaches. */
    val formLimits: FormLimits get() = FormLimits()

    /** The mark between whole and fraction, which the reading names ("Komma" · "point"). */
    val decimalMark: Char get() = '.'

    /** Accepted spellings for the level drill (sw adds the "na"-less form). */
    fun drillNumber(n: Long): List<String> = number(n)

    /**
     * Whether a leading capital in this language's readings is PUNCTUATION rather than
     * spelling. Swahili writes its clock standalone ("Saa mbili usiku") and lowercases it
     * inside a sentence; German's readings begin on nouns — Mitternacht, Mittag, Viertel —
     * whose capital is part of the word, so lowercasing one is a spelling mistake. Hence
     * the default is to leave a reading's case alone, and only a language that writes its
     * readings sentence-style says so.
     */
    val readingsCarrySentenceCapital: Boolean get() = false

    /**
     * A word a frame may carry right after the slot that a written-out reading already
     * says: German "um {slot} Uhr" meets "achtzehn Uhr fünfunddreißig", and the frame's own
     * "Uhr" gives way. Null where a frame can never double one — the digital rendering
     * keeps it either way ("Es ist jetzt 18:35 Uhr.").
     */
    val slotEcho: String? get() = null

    /**
     * Prepositions some readings LEAD with ("um acht"). Such a reading composes only where
     * the frame already carries that preposition — the duplicate is then dropped — and is
     * skipped anywhere else: "Es ist jetzt um acht." is not a time statement. A list because
     * one language may alternate by phonology (uk «о»/«об»).
     */
    val readingPrepositions: List<String> get() = emptyList()
}

private object GermanPack : TrainerLanguagePack {
    override fun number(n: Long) = GermanNumbers.variants(n)
    override fun year(y: Long) = YearReading(GermanNumbers.year(y), GermanNumbers.yearVariants(y))
    override fun clock(hour: Int, minute: Int) = GermanClock.task(hour, minute)
    override val placeValues = listOf(
        "zehn", "hundert", "tausend", "zehntausend", "hunderttausend",
        "Million", "zehn Millionen", "hundert Millionen", "Milliarde",
    )
    override val clockDayParts: Set<String> = (0..23).flatMapTo(mutableSetOf(), GermanClock::dayParts)
    override fun formReading(value: NumberValue) = GermanForms.reading(value)
    override val formLimits = GermanForms.LIMITS
    override val decimalMark = ','
    override val slotEcho = "Uhr"
    override val readingPrepositions = listOf("um ")
}

private object EnglishPack : TrainerLanguagePack {
    override fun number(n: Long) = EnglishNumbers.variants(n)
    override fun year(y: Long): YearReading {
        val variants = EnglishNumbers.yearVariants(y)
        return YearReading(variants[0], variants)
    }
    override fun clock(hour: Int, minute: Int) = EnglishClock.task(hour, minute)
    override val placeValues = listOf(
        "ten", "hundred", "thousand", "ten thousand", "hundred thousand",
        "million", "ten million", "hundred million", "billion",
    )
    override val clockDayParts: Set<String> =
        (0..23).flatMapTo(mutableSetOf(), EnglishClockRegisters::dayParts)
    override fun formReading(value: NumberValue) = EnglishForms.reading(value)
    override val formLimits = EnglishForms.LIMITS
}

private object SpanishPack : TrainerLanguagePack {
    override fun number(n: Long) = SpanishNumbers.variants(n)
    override fun year(y: Long): YearReading {
        // A year counts nothing, so it never takes the feminine agreement.
        val cardinal = SpanishNumbers.cardinal(y)
        return YearReading(cardinal, listOf(cardinal))
    }
    override fun clock(hour: Int, minute: Int) = SpanishClock.task(hour, minute)
    // Spanish has no short-scale billion: 10^9 counts as "mil millones".
    override val placeValues = listOf(
        "diez", "cien", "mil", "diez mil", "cien mil",
        "millón", "diez millones", "cien millones", "mil millones",
    )
    // why: Spanish decides on three arguments, so the vocabulary is the union over the
    // whole domain — a sentinel minute would miss a word a future rule keys on.
    override val clockDayParts: Set<String> = buildSet {
        for (h in 0..23) {
            for (m in 0..59) {
                addAll(SpanishClockForms.dayParts(h, m, countdown = false))
                addAll(SpanishClockForms.dayParts(h, m, countdown = true))
            }
        }
    }
    override fun formReading(value: NumberValue) = SpanishForms.reading(value)
    override val formLimits = SpanishForms.LIMITS
    override val decimalMark = ','
}

private object SwahiliPack : TrainerLanguagePack {
    override fun number(n: Long) = listOf(SwahiliNumbers.cardinal(n))
    override fun year(y: Long): YearReading {
        val cardinal = SwahiliNumbers.cardinal(y)
        return YearReading(cardinal, listOf(cardinal))
    }
    override fun clock(hour: Int, minute: Int) = SwahiliClock.task(hour, minute)
    override val placeValues = listOf(
        "kumi", "mia", "elfu", "elfu kumi", "elfu mia",
        "milioni", "milioni kumi", "milioni mia", "bilioni",
    )
    override val clockDayParts: Set<String> = (0..23).flatMapTo(mutableSetOf(), SwahiliClock::dayParts)
    override fun formReading(value: NumberValue) = SwahiliForms.reading(value)
    override val formLimits = SwahiliForms.LIMITS
    override fun drillNumber(n: Long) = SwahiliNumbers.acceptedVariants(n)
    // why: "Saa mbili usiku" is written as a standalone answer — the capital is the
    // sentence's, not the word's, and it drops the moment the reading goes mid-sentence.
    override val readingsCarrySentenceCapital = true
}

private object UkrainianPack : TrainerLanguagePack {
    override fun number(n: Long) = UkrainianNumbers.variants(n)
    override fun year(y: Long): YearReading {
        val variants = UkrainianNumbers.variants(y)
        return YearReading(variants[0], variants)
    }
    override fun clock(hour: Int, minute: Int) = UkrainianClock.task(hour, minute)
    override val placeValues = listOf(
        "десять", "сто", "тисяча", "десять тисяч", "сто тисяч",
        "мільйон", "десять мільйонів", "сто мільйонів", "мільярд",
    )
    override val clockDayParts: Set<String> =
        (0..23).flatMapTo(mutableSetOf(), UkrainianClockForms::dayParts)
    override fun formReading(value: NumberValue) = UkrainianForms.reading(value)
    override val formLimits = UkrainianForms.LIMITS
    override val decimalMark = ','
}

/** The registry: de/en/es/sw/uk authored, insertion order is presentation order. */
internal val trainerPacks: Map<Language, TrainerLanguagePack> = linkedMapOf(
    "de" to GermanPack,
    "en" to EnglishPack,
    "es" to SpanishPack,
    "sw" to SwahiliPack,
    "uk" to UkrainianPack,
)
