package net.spross.kern.trainer

/** Swahili cardinal numbers, ported from the prototype `NumbersTrainer.tsx`. */
internal object SwahiliNumbers {
    val ones = listOf("", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa")
    private val tens = listOf("", "kumi", "ishirini", "thelathini", "arobaini", "hamsini", "sitini", "sabini", "themanini", "tisini")

    /**
     * The tens as a labelled reference ("10 kumi" … "90 tisini") — Swahili tens
     * are the least guessable part of the system, so the drill offers a look-up.
     */
    val tensReference: List<String> get() = (1..9).map { "${it}0 ${tens[it]}" }

    /** 0..9_999_999_999; values outside fall back to digits. */
    fun cardinal(n: Long): String {
        if (n < 0) return n.toString()
        if (n == 0L) return "sifuri"
        if (n < 10) return ones[n.toInt()]
        if (n == 10L) return "kumi"
        if (n < 20) return "kumi na ${ones[(n - 10).toInt()]}"
        if (n < 100) {
            val o = (n % 10).toInt()
            val t = (n / 10).toInt()
            return if (o == 0) tens[t] else "${tens[t]} na ${ones[o]}"
        }
        if (n < 1000) {
            val h = (n / 100).toInt()
            val rest = n % 100
            val hWord = if (h == 1) "mia moja" else "mia ${ones[h]}"
            return if (rest == 0L) hWord else "$hWord na ${cardinal(rest)}"
        }
        if (n < 1_000_000) return scale(n, 1000, "elfu")
        if (n < 1_000_000_000) return scale(n, 1_000_000, "milioni")
        if (n / 1_000_000_000 <= 9) return scale(n, 1_000_000_000, "bilioni")
        return n.toString()
    }

    /**
     * Accepted spellings for the drill: the canonical reading plus one with
     * the "na" connectors dropped ("mia tatu sitini tano"), which speakers
     * routinely omit in longer numbers.
     */
    fun acceptedVariants(n: Long): List<String> {
        val canonical = cardinal(n)
        val naless = canonical.replace(" na ", " ")
        return if (naless == canonical) listOf(canonical) else listOf(canonical, naless)
    }

    /**
     * "elfu moja / milioni mbili / bilioni tatu [na rest]": one scale word,
     * "moja" for a bare 1, else the cardinal of the multiplier, rest joined
     * with "na".
     */
    private fun scale(n: Long, unit: Long, word: String): String {
        val t = n / unit
        val rest = n % unit
        val tWord = if (t == 1L) "$word moja" else "$word ${cardinal(t)}"
        return if (rest == 0L) tWord else "$tWord na ${cardinal(rest)}"
    }
}
