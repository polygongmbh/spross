package net.spross.kern.trainer

/**
 * German cardinal numbers and year readings. Ported from the prototype
 * `NumbersTrainer.tsx` (source of truth), with one fix: tens compounds use
 * "ein" ("einundzwanzig"), not the prototype's erroneous "einsundzwanzig".
 */
internal object GermanNumbers {
    private val ones = listOf("", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun")
    private val teens = listOf("zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn")
    private val tens = listOf("", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig")

    /** 0..9_999_999_999; values outside fall back to digits. */
    fun cardinal(n: Long): String {
        if (n < 0) return n.toString()
        if (n == 0L) return "null"
        if (n < 10) return ones[n.toInt()]
        if (n < 20) return teens[(n - 10).toInt()]
        if (n < 100) {
            val o = (n % 10).toInt()
            val t = (n / 10).toInt()
            if (o == 0) return tens[t]
            return (if (o == 1) "ein" else ones[o]) + "und" + tens[t]
        }
        if (n < 1000) {
            val h = (n / 100).toInt()
            val rest = n % 100
            val hWord = if (h == 1) "einhundert" else ones[h] + "hundert"
            return if (rest == 0L) hWord else hWord + cardinal(rest)
        }
        if (n < 1_000_000) {
            val t = n / 1000
            val rest = n % 1000
            val tWord = if (t == 1L) "eintausend" else cardinal(t) + "tausend"
            return if (rest == 0L) tWord else tWord + cardinal(rest)
        }
        // Millions and above are written as separate words in German
        // ("eine Million", "zwei Millionen dreihunderttausend"); supported
        // through single-digit billions, larger values fall back to digits.
        val billions = n / 1_000_000_000
        if (billions > 9) return n.toString()
        val parts = mutableListOf<String>()
        var rest = n % 1_000_000_000
        if (billions > 0) parts += scaleWord(billions, "eine Milliarde", "Milliarden")
        val millions = rest / 1_000_000
        rest %= 1_000_000
        if (millions > 0) parts += scaleWord(millions, "eine Million", "Millionen")
        if (rest > 0) parts += cardinal(rest)
        return parts.joinToString(" ")
    }

    /**
     * "eine Million" / "zwei Millionen" style: singular gets the "eine" form,
     * everything else counts with the cardinal + plural scale word.
     */
    private fun scaleWord(count: Long, one: String, many: String): String =
        if (count == 1L) one else cardinal(count) + " " + many

    /** Every accepted spelling of a cardinal, canonical first. */
    fun variants(n: Long): List<String> = bareStems(cardinal(n))

    /**
     * "einhundert…"/"eintausend…" is what the generator writes and is not wrong, but bare
     * "hundert"/"tausend" is what people say — and it is unreachable from the cardinal, so
     * it is added here. The LEADING one only: "eintausendeinhundert" drops its first "ein"
     * ("tausendeinhundert") and keeps the medial one, which no register drops.
     */
    fun bareStems(stem: String): List<String> =
        if (stem.startsWith("einhundert") || stem.startsWith("eintausend")) {
            listOf(stem, stem.removePrefix("ein"))
        } else {
            listOf(stem)
        }

    /** "neunzehnhundertachtundsiebzig" style for years like 1978. */
    fun yearHundred(y: Long): String {
        val century = y / 100
        val rest = y % 100
        if (rest == 0L) return cardinal(century) + "hundert"
        return cardinal(century) + "hundert" + cardinal(rest)
    }

    /** Canonical year reading: hundred-counting is standard for 1100–1999. */
    fun year(y: Long): String =
        if (y in 1100..1999 && y % 1000 != 0L) yearHundred(y) else cardinal(y)

    /** All accepted year readings (plain cardinal + hundred-counting where idiomatic). */
    fun yearVariants(y: Long): List<String> {
        val variants = mutableListOf(cardinal(y))
        if (y in 1100..1999 && y % 1000 != 0L) variants += yearHundred(y)
        if (y >= 2000 && y % 1000 != 0L) variants += yearHundred(y)
        return variants.flatMap(::bareStems).distinct()
    }
}
