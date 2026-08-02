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

    /** Tens look-up, authored only where tens are hard to recall (sw). */
    val tensReference: List<String>? get() = null

    /** Accepted spellings for the level drill (sw adds the "na"-less form). */
    fun drillNumber(n: Long): List<String> = number(n)
}

private object GermanPack : TrainerLanguagePack {
    override fun number(n: Long) = listOf(GermanNumbers.cardinal(n))
    override fun year(y: Long) = YearReading(GermanNumbers.year(y), GermanNumbers.yearVariants(y))
    override fun clock(hour: Int, minute: Int) = GermanClock.task(hour, minute)
    override val placeValues = listOf(
        "zehn", "hundert", "tausend", "zehntausend", "hunderttausend",
        "Million", "zehn Millionen", "hundert Millionen", "Milliarde",
    )
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
}

private object SwahiliPack : TrainerLanguagePack {
    override fun number(n: Long) = listOf(SwahiliNumbers.cardinal(n))
    override fun year(y: Long): YearReading {
        val cardinal = SwahiliNumbers.cardinal(y)
        return YearReading(cardinal, listOf(cardinal))
    }
    override fun clock(hour: Int, minute: Int) =
        ClockReading(SwahiliClock.time(hour, minute), SwahiliClock.accepted(hour, minute), SwahiliClock.GLOSS)
    override val placeValues = listOf(
        "kumi", "mia", "elfu", "elfu kumi", "elfu mia",
        "milioni", "milioni kumi", "milioni mia", "bilioni",
    )
    override val tensReference get() = SwahiliNumbers.tensReference
    override fun drillNumber(n: Long) = SwahiliNumbers.acceptedVariants(n)
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
}

/** The registry: de/en/es/sw/uk authored, insertion order is presentation order. */
internal val trainerPacks: Map<Language, TrainerLanguagePack> = linkedMapOf(
    "de" to GermanPack,
    "en" to EnglishPack,
    "es" to SpanishPack,
    "sw" to SwahiliPack,
    "uk" to UkrainianPack,
)
