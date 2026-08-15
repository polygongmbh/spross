package net.spross.kern.trainer

/**
 * Italian cardinal numbers. Everything below a million is ONE word — "centoventitré",
 * "millenovecentosettantotto" — which makes the SEAMS the whole of the spelling rule:
 * a ten drops its final vowel before uno and otto, cento drops its own before another o,
 * mille and -mila drop nothing, and a compound ending in tre takes the acute accent.
 * All four are applied in [weld] and [spellUnderHundred] rather than tabulated.
 *
 * The reading is the CITATION form. Italian apocopates a numeral standing before a noun
 * ("ventun volte") and lets uno agree with it ("una volta"), but both need the noun, which
 * a bare prompt has not got — so the forms that carry one build it ([ItalianForms]).
 */
internal object ItalianNumbers {

    private val ones = listOf(
        "", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove",
    )
    private val teens = listOf(
        "dieci", "undici", "dodici", "tredici", "quattordici",
        "quindici", "sedici", "diciassette", "diciotto", "diciannove",
    )
    private val tens = listOf(
        "", "", "venti", "trenta", "quaranta", "cinquanta",
        "sessanta", "settanta", "ottanta", "novanta",
    )

    /** Canonical reading. 0..9_999_999_999; beyond that the digits stand. */
    fun cardinal(n: Long): String = compose(n)

    /**
     * Canonical first, then the spellings the dictionaries record beside it: the hiatus
     * "centootto" behind the contracted "centotto", and — where a scale word leaves a
     * tail — the `e` speakers put in front of it.
     *
     * The elided "centuno" is the one recorded spelling this pack leaves out. It sits a
     * single substitution from "ventuno", so a drill accepting it would take 21 for 101,
     * and the hiatus "centouno" the dictionaries head their entry with says the number
     * unambiguously.
     */
    fun variants(n: Long): List<String> {
        val canonical = compose(n)
        val readings = mutableListOf(canonical)
        if ("centotto" in canonical) readings += canonical.replace("centotto", "centootto")
        val words = canonical.split(' ')
        // The last GROUP is what the e goes in front of, which is the two words of a
        // scale-word group ("un miliardo e un milione") and one word otherwise.
        val tail = if (words.last() in SCALE_WORDS) 2 else 1
        if (words.size > tail) {
            readings += words.dropLast(tail).joinToString(" ") + " e " + words.takeLast(tail).joinToString(" ")
        }
        return readings.distinct()
    }

    private val SCALE_WORDS = setOf("milione", "milioni", "miliardo", "miliardi")

    private fun compose(n: Long): String {
        if (n < 0) return n.toString()
        if (n == 0L) return "zero"
        if (n / 1_000_000_000 > 9) return n.toString()
        val words = mutableListOf<String>()
        var rest = n
        // Italian is short-scale at this reach: miliardo IS 10^9, and only bilione (10^12)
        // leaves the pattern — past the drill's ceiling, so nothing here has to know it.
        val billions = rest / 1_000_000_000
        rest %= 1_000_000_000
        if (billions > 0) {
            words += if (billions == 1L) "un miliardo" else underThousand(billions.toInt()) + " miliardi"
        }
        val millions = rest / 1_000_000
        rest %= 1_000_000
        if (millions > 0) {
            words += if (millions == 1L) "un milione" else underThousand(millions.toInt()) + " milioni"
        }
        if (rest > 0) words += underMillion(rest)
        return words.joinToString(" ")
    }

    /** 1..999_999, welded into the single word Italian writes below a million. */
    private fun underMillion(n: Long): String {
        val thousands = (n / 1000).toInt()
        val rest = (n % 1000).toInt()
        // A bare thousand is "mille"; every multiple takes the -mila form ("duemila").
        val head = when (thousands) {
            0 -> ""
            1 -> "mille"
            else -> underThousand(thousands) + "mila"
        }
        return weld(head, if (rest == 0) "" else underThousand(rest))
    }

    /** 1..999. cento is invariable, so the multiplier simply precedes it ("duecento"). */
    private fun underThousand(n: Int): String {
        val hundreds = n / 100
        val head = when (hundreds) {
            0 -> ""
            1 -> "cento"
            else -> ones[hundreds] + "cento"
        }
        return weld(head, if (n % 100 == 0) "" else spellUnderHundred(n % 100))
    }

    private fun spellUnderHundred(n: Int): String = when {
        n < 10 -> ones[n]
        n < 20 -> teens[n - 10]
        n % 10 == 0 -> tens[n / 10]
        // The ten loses its own vowel before both vowel-initial units, and only those.
        n % 10 == 1 || n % 10 == 8 -> tens[n / 10].dropLast(1) + ones[n % 10]
        n % 10 == 3 -> tens[n / 10] + "tré"
        else -> tens[n / 10] + ones[n % 10]
    }

    /**
     * The seam between two parts of one written-together numeral.
     *
     * cento gives its final o up to a following o ("centotto", "duecentottanta") and keeps
     * it everywhere else ("centouno", "centoundici"); mille and -mila keep theirs against
     * anything ("milleotto", "duemilaotto"). And tre, alone at the end of a compound, is
     * where the word's stress lands, so it is written with the accent ("centotré").
     */
    private fun weld(head: String, tail: String): String = when {
        head.isEmpty() -> tail
        tail.isEmpty() -> head
        tail == "tre" -> head + "tré"
        head.endsWith('o') && tail.startsWith('o') -> head.dropLast(1) + tail
        else -> head + tail
    }
}
