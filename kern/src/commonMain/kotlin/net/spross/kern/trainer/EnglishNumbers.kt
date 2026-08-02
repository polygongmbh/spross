package net.spross.kern.trainer

/**
 * English cardinal numbers and year readings. Canonical spelling is the
 * short-scale reading with hyphenated compound tens ("three hundred
 * forty-seven"); the British "and" form and the unhyphenated spelling are
 * accepted alongside it — the comparison pipeline deletes hyphens but not
 * spaces, so "twenty one" is a genuinely different form from "twenty-one".
 */
internal object EnglishNumbers {
    private val ones = listOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    private val teens = listOf("ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private val scales = listOf(1_000_000_000L to "billion", 1_000_000L to "million", 1000L to "thousand")

    /** 0..9_999_999_999; values outside fall back to digits. */
    fun cardinal(n: Long): String = compose(n, useAnd = false)

    /** Canonical reading first, then the "and" form, each in both spellings. */
    fun variants(n: Long): List<String> =
        spellings(listOf(compose(n, useAnd = false), compose(n, useAnd = true)))

    /**
     * Canonical year reading: pair-counting ("nineteen seventy-eight") wherever
     * it is idiomatic, i.e. every four-digit year that is not a round thousand.
     * 2000–2009 are the exception — "two thousand five" is the ordinary reading
     * there and "twenty oh five" the marginal one.
     */
    fun year(y: Long): String = yearVariants(y).first()

    /** All accepted year readings, canonical first. */
    fun yearVariants(y: Long): List<String> {
        val cardinals = listOf(compose(y, useAnd = false), compose(y, useAnd = true))
        if (!pairReadable(y)) return spellings(cardinals)
        val pairs = pairReadings(y)
        return spellings(if (y in 2000..2009) cardinals + pairs else pairs + cardinals)
    }

    /** Words for 1..59 without a scale word — the clock's minute reading. */
    fun underHundred(n: Int): String = when {
        n == 0 -> ""
        n < 10 -> ones[n]
        n < 20 -> teens[n - 10]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + "-" + ones[n % 10]
    }

    /** Every reading in both spellings: hyphenated compound tens and spaced. */
    fun spellings(readings: List<String>): List<String> =
        readings.flatMap { listOf(it, it.replace("-", " ")) }.distinct()

    private fun pairReadable(y: Long): Boolean = y in 1000..9999 && y % 1000 != 0L

    /** "nineteen seventy-eight" · "nineteen hundred" · "nineteen oh five". */
    private fun pairReadings(y: Long): List<String> {
        val century = cardinal(y / 100)
        val rest = (y % 100).toInt()
        return when {
            rest == 0 -> listOf("$century hundred")
            rest < 10 -> listOf(
                "$century oh ${ones[rest]}",
                "$century hundred ${ones[rest]}",
                "$century hundred and ${ones[rest]}",
            )
            else -> listOf("$century ${underHundred(rest)}")
        }
    }

    private fun compose(n: Long, useAnd: Boolean): String {
        if (n < 0) return n.toString()
        if (n == 0L) return "zero"
        if (n / 1_000_000_000 > 9) return n.toString()
        val parts = mutableListOf<String>()
        var rest = n
        for ((unit, word) in scales) {
            val count = rest / unit
            if (count > 0) {
                parts += underThousand(count.toInt(), useAnd) + " " + word
                rest %= unit
            }
        }
        if (rest > 0) {
            val tail = underThousand(rest.toInt(), useAnd)
            // "and" also bridges a scale word and a remainder below a hundred
            // ("one thousand and five"), the British reading of that gap.
            parts += if (useAnd && parts.isNotEmpty() && rest < 100) "and $tail" else tail
        }
        return parts.joinToString(" ")
    }

    private fun underThousand(n: Int, useAnd: Boolean): String {
        val head = if (n >= 100) ones[n / 100] + " hundred" else ""
        val tail = underHundred(n % 100)
        return when {
            head.isEmpty() -> tail
            tail.isEmpty() -> head
            useAnd -> "$head and $tail"
            else -> "$head $tail"
        }
    }
}
