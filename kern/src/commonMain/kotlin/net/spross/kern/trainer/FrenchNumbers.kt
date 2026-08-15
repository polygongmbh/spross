package net.spross.kern.trainer

/**
 * French cardinal numbers and year readings.
 *
 * The canonical spelling is the TRADITIONAL orthography — a hyphen inside a compound below
 * a hundred, spaces around `cent` and `mille`, and `et` where 21…71 take it. Beside it grade
 * the 1990-rectified all-hyphen spelling and the fully spaced twin of both: the comparison
 * pipeline DELETES hyphens rather than spacing them, so `vingt-et-un` is one word and
 * `vingt et un` three, and only emitting both keeps either from booking a right answer amber.
 *
 * The regional decades (`septante`, `nonante`, and Swiss `huitante`) grade beside the
 * vigesimal standard forms; which varieties they belong to, and why `octante` is left out,
 * is `docs/number-forms.md` § French.
 */
internal object FrenchNumbers {

    private val units = listOf(
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
    )

    private val decades = listOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante")

    /** The scale NOUNS, which pluralize and never weld into the numeral run around them. */
    private val scales = listOf(1_000_000_000L to "milliard", 1_000_000L to "million")

    /** How a variety counts 70/80/90: on twenty, or on decade words of its own. */
    private enum class Tens { Standard, Belgian, Swiss }

    /** Canonical reading — traditional spelling, standard French. 0..9_999_999_999. */
    fun cardinal(n: Long): String = variants(n).first()

    /** The same reading with a feminine ONE: `une heure`, `vingt et une fois`. */
    fun feminine(n: Long): String = feminineVariants(n).first()

    /** Canonical first, then the rectified and spaced spellings and the regional decades. */
    fun variants(n: Long): List<String> = spellings(n, feminine = false)

    /** [variants] of the reading whose ONE agrees with a feminine noun (`fois`, `heure`). */
    fun feminineVariants(n: Long): List<String> = spellings(n, feminine = true)

    /** Words for 1..59 without a scale word — the clock's minute count, which is feminine. */
    fun minute(n: Int): String = underHundred(n, Tens.Standard, feminine = true, standalone = true)

    /**
     * Year readings, canonical first. A year is a plain cardinal, and takes two spellings
     * besides: the date form `mil`, and the hundred-counting `dix-neuf cent …` that every
     * year from 1100 to 1999 also has.
     */
    fun yearVariants(y: Long): List<String> {
        if (y !in 0..9_999) return variants(y)
        val out = mutableListOf<String>()
        for (style in Tens.entries) {
            val cardinal = render(groups(y, style, feminine = false), hyphenate = false)
            out += spellingsOf(cardinal)
            if (y in 1001..1999) out += spellingsOf(cardinal.replaceFirst("mille", "mil"))
            if (y in 1100..1999) out += spellingsOf(hundredStyle(y.toInt(), style))
        }
        return out.distinct()
    }

    private fun spellings(n: Long, feminine: Boolean): List<String> {
        if (n < 0 || n / 1_000_000_000 > 9) return listOf(n.toString())
        val out = mutableListOf<String>()
        for (style in Tens.entries) {
            val groups = groups(n, style, feminine)
            val traditional = render(groups, hyphenate = false)
            out += traditional
            out += render(groups, hyphenate = true)
            out += traditional.replace('-', ' ')
        }
        return out.distinct()
    }

    /** Traditional, rectified and spaced — for a run of numerals carrying no scale noun. */
    private fun spellingsOf(numeral: String): List<String> =
        listOf(numeral, numeral.replace(' ', '-'), numeral.replace('-', ' ')).distinct()

    /**
     * One numeral run and, where it counts a scale NOUN, that noun. The split is what the
     * rectified spelling needs: the 1990 hyphens tie the numerals of one run together and
     * stop at `million`/`milliard`, which are nouns rather than numeral adjectives.
     */
    private data class Group(val numeral: String, val noun: String? = null)

    private fun groups(n: Long, style: Tens, feminine: Boolean): List<Group> {
        if (n == 0L) return listOf(Group("zéro"))
        val out = mutableListOf<Group>()
        var rest = n
        for ((unit, noun) in scales) {
            val count = (rest / unit).toInt()
            if (count == 0) continue
            rest %= unit
            out += Group(
                underThousand(count, style, feminine = false, followed = false),
                if (count == 1) noun else noun + "s",
            )
        }
        if (rest > 0) out += Group(underMillion(rest.toInt(), style, feminine))
        return out
    }

    private fun render(groups: List<Group>, hyphenate: Boolean): String =
        groups.joinToString(" ") { group ->
            val numeral = if (hyphenate) group.numeral.replace(' ', '-') else group.numeral
            if (group.noun == null) numeral else "$numeral ${group.noun}"
        }

    private fun underMillion(n: Int, style: Tens, feminine: Boolean): String {
        val thousands = n / 1000
        val rest = n % 1000
        val parts = mutableListOf<String>()
        // A bare thousand is "mille", never "un mille", and mille never takes an -s.
        if (thousands > 0) {
            parts += if (thousands == 1) {
                "mille"
            } else {
                underThousand(thousands, style, feminine = false, followed = true) + " mille"
            }
        }
        if (rest > 0) parts += underThousand(rest, style, feminine, followed = false)
        return parts.joinToString(" ")
    }

    /**
     * [followed] says another NUMERAL comes after this run — which is exactly when a
     * multiplied `cent` or `vingt` drops its plural -s (`deux cent mille`, `quatre-vingt
     * mille`), while a following scale noun leaves it standing (`deux cents millions`).
     */
    private fun underThousand(n: Int, style: Tens, feminine: Boolean, followed: Boolean): String {
        val hundreds = n / 100
        val rest = n % 100
        val head = when {
            hundreds == 0 -> ""
            hundreds == 1 -> "cent"
            else -> units[hundreds] + " cent" + if (rest == 0 && !followed) "s" else ""
        }
        val tail = underHundred(rest, style, feminine, standalone = !followed)
        return listOf(head, tail).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun underHundred(n: Int, style: Tens, feminine: Boolean, standalone: Boolean): String {
        val one = if (feminine) "une" else "un"
        return when {
            n == 0 -> ""
            n == 1 -> one
            n <= 16 -> units[n]
            n <= 19 -> "dix-" + units[n - 10]
            n < 70 -> decade(decades[n / 10], n % 10, one)
            n < 80 -> if (style == Tens.Standard) vigesimal(n, one, standalone) else decade("septante", n - 70, one)
            n < 90 -> if (style == Tens.Swiss) decade("huitante", n - 80, one) else vigesimal(n, one, standalone)
            else -> if (style == Tens.Standard) vigesimal(n, one, standalone) else decade("nonante", n - 90, one)
        }
    }

    /** A decade word and its unit: `vingt` · `vingt et un` · `vingt-deux`. */
    private fun decade(word: String, unit: Int, one: String): String = when (unit) {
        0 -> word
        1 -> "$word et $one"
        else -> "$word-" + units[unit]
    }

    /**
     * Standard French counts 70–99 on twenty. `et` reaches 71 and stops there —
     * `quatre-vingt-un` and `quatre-vingt-onze` take none — and `quatre-vingts` keeps its
     * plural -s only as the last numeral of its run.
     */
    private fun vigesimal(n: Int, one: String, standalone: Boolean): String = when {
        n == 71 -> "soixante et onze"
        n < 80 -> "soixante-" + segment(n - 60)
        n == 80 -> if (standalone) "quatre-vingts" else "quatre-vingt"
        n == 81 -> "quatre-vingt-$one"
        else -> "quatre-vingt-" + segment(n - 80)
    }

    /** 2..19 as one written segment: the unit word, or the `dix-` compound from 17 up. */
    private fun segment(n: Int): String = if (n <= 16) units[n] else "dix-" + units[n - 10]

    /** `dix-neuf cent soixante-dix-huit` — the hundred-counting date reading. */
    private fun hundredStyle(y: Int, style: Tens): String {
        val hundreds = underThousand(y / 100, style, feminine = false, followed = true)
        val rest = y % 100
        val head = "$hundreds cent" + if (rest == 0) "s" else ""
        val tail = underHundred(rest, style, feminine = false, standalone = true)
        return listOf(head, tail).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
