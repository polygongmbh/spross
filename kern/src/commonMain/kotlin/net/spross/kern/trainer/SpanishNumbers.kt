package net.spross.kern.trainer

/**
 * Spanish cardinal numbers. Canonical form counts masculine and unapocopated
 * ("veintiuno", "doscientos"); the apocopated form before a masculine noun
 * ("veintiún", "treinta y un") and the feminine agreement ("veintiuna",
 * "doscientas") are accepted variants — a learner types whichever the frame
 * around the slot suggested. Years read as plain cardinals ("mil novecientos
 * setenta y ocho").
 */
internal object SpanishNumbers {
    private val ones = listOf("", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve")
    private val teens = listOf("diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve")
    private val twenties = listOf("veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve")
    private val tens = listOf("", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa")
    private val hundreds = listOf("", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos")

    /** How a trailing "uno" and the -cientos ending agree with what is counted. */
    internal enum class Form { Masculine, Apocopated, Feminine }

    /** Canonical (masculine, unapocopated) reading. 0..9_999_999_999. */
    fun cardinal(n: Long): String = compose(n, Form.Masculine)

    /**
     * The reading in a chosen agreement. The number forms need all three inside one
     * pack — "uno por ciento" (unapocopated), "una vez" (vez is feminine), "un tercio"
     * (apocopated before a masculine noun) — so a single "one" would be wrong twice.
     */
    fun cardinal(n: Long, form: Form): String = compose(n, form)

    /** Canonical first, then the apocopated and feminine agreements. */
    fun variants(n: Long): List<String> =
        Form.entries.map { compose(n, it) }.distinct()

    /** Words for 1..59 without a scale word — the clock's minute reading. */
    fun underHundred(n: Int): String = underHundred(n, Form.Masculine)

    private fun compose(n: Long, form: Form): String {
        if (n < 0) return n.toString()
        if (n == 0L) return "cero"
        if (n / 1_000_000_000 > 9) return n.toString()
        val words = mutableListOf<String>()
        var rest = n
        val millions = rest / 1_000_000
        rest %= 1_000_000
        // Spanish has no short-scale billion: 9 999 999 999 is
        // "nueve mil novecientos noventa y nueve millones …".
        if (millions > 0) {
            words += if (millions == 1L) "un millón" else compose(millions, Form.Apocopated) + " millones"
        }
        val thousands = rest / 1000
        rest %= 1000
        // A bare thousand is "mil", never "un mil".
        if (thousands > 0) {
            words += if (thousands == 1L) "mil" else underThousand(thousands.toInt(), Form.Apocopated) + " mil"
        }
        if (rest > 0) words += underThousand(rest.toInt(), form)
        return words.joinToString(" ")
    }

    private fun underThousand(n: Int, form: Form): String {
        if (n == 100) return "cien"
        val h = n / 100
        val head = when {
            h == 0 -> ""
            h == 1 -> "ciento"
            form == Form.Feminine -> hundreds[h].removeSuffix("os") + "as"
            else -> hundreds[h]
        }
        val tail = underHundred(n % 100, form)
        return listOf(head, tail).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun underHundred(n: Int, form: Form): String = when {
        n == 0 -> ""
        n < 10 -> unit(n, form)
        n < 20 -> teens[n - 10]
        n == 21 -> when (form) {
            Form.Feminine -> "veintiuna"
            Form.Apocopated -> "veintiún"
            Form.Masculine -> "veintiuno"
        }
        n < 30 -> twenties[n - 20]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + " y " + unit(n % 10, form)
    }

    private fun unit(n: Int, form: Form): String = when {
        n != 1 -> ones[n]
        form == Form.Feminine -> "una"
        form == Form.Apocopated -> "un"
        else -> "uno"
    }
}
