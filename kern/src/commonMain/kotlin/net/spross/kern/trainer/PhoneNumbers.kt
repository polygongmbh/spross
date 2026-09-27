package net.spross.kern.trainer

import kotlin.random.Random

/**
 * How one language writes and reads a phone number — a mobile number of the country whose
 * speakers the pack teaches, grouped the way that country writes it
 * (`docs/phone-readings.md`).
 *
 * [prefixes] are the leading digits a plausible number opens with (a network or area code);
 * the rest is drawn at random. [read] takes the written groups and returns every accepted
 * reading, canonical first.
 */
internal class PhonePlan(
    private val groups: List<Int>,
    private val prefixes: List<String>,
    private val read: (List<String>) -> List<String>,
) {
    private val length = groups.sum()

    fun draw(rng: Random): String {
        val prefix = prefixes[rng.nextInt(prefixes.size)]
        return prefix + (prefix.length until length).map { '0' + rng.nextInt(10) }.joinToString("")
    }

    /** The digits as the country writes them, groups held together by U+00A0. */
    fun grouped(digits: String): String = split(digits).joinToString(" ")

    fun readings(digits: String): List<String> = read(split(digits))

    private fun split(digits: String): List<String> {
        require(digits.length == length && digits.all(Char::isDigit)) { "not a number of this plan: $digits" }
        var start = 0
        return groups.map { size -> digits.substring(start, start + size).also { start += size } }
    }
}

/**
 * One reading per STYLE — every group read the same way, never a style mixed inside one
 * number: [words] gives each group's readings in style order, and a group with fewer
 * styles than the rest reads its canonical one there.
 */
private fun styled(words: List<List<String>>, separator: String = ", "): List<String> =
    (0 until words.maxOf { it.size })
        .map { style -> words.joinToString(separator) { group -> group.getOrElse(style) { group[0] } } }
        .distinct()

/** A group read digit by digit, in each style [digit] offers ("zero" · "oh"). */
private fun digitwise(group: String, digit: (Int) -> List<String>): List<String> =
    styled(group.map { digit(it.digitToInt()) }, separator = " ")

/**
 * A group read as the number it spells, a leading zero spoken as [zero] first:
 * `067` is "нуль шістдесят сім", `005` "нуль нуль п'ять".
 */
private fun numbered(group: String, zero: String, cardinal: (Long) -> List<String>): List<String> {
    val zeros = group.takeWhile { it == '0' }.map { zero }.joinToString(" ")
    val rest = group.dropWhile { it == '0' }
    if (rest.isEmpty()) return listOf(zeros)
    return cardinal(rest.toLong()).map { if (zeros.isEmpty()) it else "$zeros $it" }
}

internal object PhonePlans {

    /**
     * DIN 5008 writes a mobile as network code plus one unbroken subscriber block.
     * Read digit by digit, with the telephone's `zwo` for every `zwei` beside it,
     * or with the subscriber block in pairs.
     */
    val German = PhonePlan(
        groups = listOf(4, 8),
        prefixes = listOf("0151", "0152", "0157", "0160", "0162", "0170", "0171", "0172",
                          "0173", "0174", "0175", "0176", "0177", "0178", "0179"),
    ) { (network, subscriber) ->
        val digit = { d: Int -> listOf(GermanNumbers.cardinal(d.toLong())) }
        val pairs = subscriber.chunked(2).map { numbered(it, "null") { n -> listOf(GermanNumbers.cardinal(n)) } }
        val readings = listOf(
            styled(listOf(digitwise(network, digit), digitwise(subscriber, digit))),
            styled(listOf(digitwise(network, digit)) + pairs),
        ).flatten()
        (readings + readings.map { it.replace(Regex("\\bzwei\\b"), "zwo") }).distinct()
    }

    /** US numbers, area code · exchange · line, every digit said on its own. */
    val English = PhonePlan(
        groups = listOf(3, 3, 4),
        prefixes = listOf("206", "212", "305", "312", "415", "503", "512", "617", "702", "808", "919")
            .flatMap { area -> (2..9).map { "$area$it" } },
    ) { groups ->
        val digit = { d: Int -> if (d == 0) listOf("zero", "oh") else listOf(EnglishNumbers.cardinal(d.toLong())) }
        styled(groups.map { digitwise(it, digit) })
    }

    /** Written and read in pairs, `06 12 34 56 78`, each pair the number it spells. */
    val French = PhonePlan(groups = List(5) { 2 }, prefixes = listOf("06", "07")) { groups ->
        styled(groups.map { numbered(it, "zéro", FrenchNumbers::variants) })
    }

    /** Spain's mobiles in threes, each group read as its number, or every digit on its own. */
    val Spanish = PhonePlan(groups = listOf(3, 3, 3), prefixes = listOf("6", "7")) { groups ->
        styled(groups.map { numbered(it, "cero") { n -> listOf(SpanishNumbers.cardinal(n)) } }) +
            styled(groups.map { g -> digitwise(g) { d -> listOf(SpanishNumbers.cardinal(d.toLong())) } })
    }

    /** Italian mobiles open on a 3, and are read one digit at a time. */
    val Italian = PhonePlan(
        groups = listOf(3, 3, 4),
        prefixes = listOf("320", "328", "329", "333", "334", "335", "338", "339", "340", "342",
                          "345", "347", "348", "349", "351", "366", "370", "380", "388", "389", "393"),
    ) { groups ->
        styled(groups.map { g -> digitwise(g) { d -> listOf(ItalianNumbers.cardinal(d.toLong())) } })
    }

    /** `067 123 45 67`, each group read as its number, or every digit on its own. */
    val Ukrainian = PhonePlan(
        groups = listOf(3, 3, 2, 2),
        prefixes = listOf("050", "063", "066", "067", "068", "073", "093", "095", "096", "097", "098", "099"),
    ) { groups ->
        styled(groups.map { numbered(it, "нуль") { n -> listOf(UkrainianNumbers.cardinal(n)) } }) +
            styled(groups.map { g -> digitwise(g) { d -> listOf(UkrainianNumbers.cardinal(d.toLong())) } })
    }

    /** Tanzanian mobiles, `0712 345 678`, read one digit at a time. */
    val Swahili = PhonePlan(
        groups = listOf(4, 3, 3),
        prefixes = listOf("062", "065", "067", "068", "071", "074", "075", "076", "077", "078"),
    ) { groups ->
        styled(groups.map { g -> digitwise(g) { d -> listOf(SwahiliNumbers.cardinal(d.toLong())) } })
    }

    /**
     * No country's plan is Esperanto's, so the number keeps the plain four-three-three
     * shape and is read digit by digit, zero as the numeral `nul` with the noun `nulo` beside it.
     */
    val Esperanto = PhonePlan(groups = listOf(4, 3, 3), prefixes = listOf("0")) { groups ->
        val readings = styled(groups.map { g ->
            digitwise(g) { d -> if (d == 0) listOf("nul", "nulo") else listOf(EsperantoNumbers.cardinal(d.toLong())) }
        })
        EsperantoNumbers.spellings(readings)
    }
}
