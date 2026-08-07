package net.spross.kern.trainer

import net.spross.kern.trainer.SpanishNumbers.Form

/**
 * Spanish readings of the written number forms
 * (RAE: DPD «ordinales» · «fraccionarios», Ortografía, «veintiuna personas, veintiuno por ciento»).
 *
 * Ordinals stop at 12. Not because «septuagésimo» is long, but because the Nueva gramática
 * records "una marcada tendencia a evitar el uso de los ordinales más allá de los
 * correspondientes a la segunda o tercera decenas" — past that speakers say the cardinal
 * (el piso veinte), so drilling «vigésimo primero» would teach a register nobody uses.
 * 12 is also the seam where the etymological undécimo/duodécimo stop and the analogical
 * decimotercero series begins. Fractions need no such cap: -avo is productive and
 * onceavo/doceavo are school vocabulary.
 *
 * The number 1 is read three ways inside this one pack, which is why every arm below names
 * its [Form] instead of taking the default cardinal.
 */
internal object SpanishForms {

    val LIMITS = FormLimits(
        forms = NumberForm.entries.toSet(),
        fractionDenominators = (2..12).toSet(),
        ordinalRange = 1L..12L,
    )

    fun reading(value: NumberValue): List<String> = when (value) {
        is NumberValue.Negative -> listOf("menos " + SpanishNumbers.cardinal(value.magnitude))
        is NumberValue.Decimal -> decimal(value.whole, value.fractionDigits)
        is NumberValue.Percent -> percent(value.n)
        is NumberValue.Multiplicative -> multiplicative(value.n)
        is NumberValue.Fraction -> fraction(value.numerator, value.denominator)
        is NumberValue.Ordinal -> ordinal(value.n)
    }

    /**
     * Digit-by-digit leads, with the run-together reading of the fractional part accepted
     * alongside — both are sanctioned for "3,14", and the run-together one is traditionally
     * commoner in Spanish than in German. It is still suppressed on a leading zero, where it
     * would name a different number.
     */
    private fun decimal(whole: Long, fractionDigits: String): List<String> {
        val head = SpanishNumbers.cardinal(whole)
        val tails = mutableListOf(
            fractionDigits
                .map { SpanishNumbers.cardinal(it.digitToInt().toLong()) }
                .joinToString(" "),
        )
        if (fractionDigits.length >= 2 && fractionDigits.first() != '0') {
            tails += SpanishNumbers.cardinal(fractionDigits.toLong())
        }
        // why: RAE's Ortografía admits both separators — coma in Spain, Argentina, Chile,
        // Colombia and Peru, punto in Mexico, Central America and the Caribbean — so the mark
        // the prompt happened to draw must not decide whether the answer grades.
        return listOf("coma", "punto").flatMap { mark -> tails.map { "$head $mark $it" } }.distinct()
    }

    /**
     * The MASCULINE UNAPOCOPATED cardinal: «por ciento» is not a noun, and RAE is explicit
     * that "no debe decirse *«el veintiún por ciento», sino «el veintiuno por ciento»".
     * One-word "porciento" is a Caribbean noun meaning porcentaje and is wrong after a
     * numeral, so it never grades either.
     */
    private fun percent(n: Long): List<String> {
        val readings = mutableListOf(SpanishNumbers.cardinal(n) + " por ciento")
        if (n == 100L) readings += listOf("cien por cien", "ciento por ciento")
        return readings
    }

    /**
     * Frequency, and therefore FEMININE: vez is a feminine noun and uno agrees in gender
     * with the noun it immediately precedes ("veintiuna veces", never "veintiún veces").
     * The quantity multiplicatives (doble, triple) are a different family and stay out.
     */
    private fun multiplicative(n: Long): List<String> =
        listOf(SpanishNumbers.cardinal(n, Form.Feminine) + if (n == 1L) " vez" else " veces")

    /**
     * Apocopated numerator ("un tercio") plus the denominator noun, pluralized by the
     * numerator. Denominators 4..10 coincide with the masculine ordinal (DPD); 2 and 3 are
     * suppletive; from 11 the productive -avo leads with the etymological ordinal accepted
     * beside it ("admiten ambas formas … aunque hoy suelen preferirse las primeras").
     *
     * Only medio modifies a noun directly, so the feminine «parte» construction is a variant
     * rather than the bare canonical reading. Since 1 ≤ n < d, d == 2 forces n == 1.
     */
    private fun fraction(n: Long, d: Long): List<String> {
        val numerator = if (n == 1L) "un" else SpanishNumbers.cardinal(n)
        if (d == 2L) return listOf("$numerator medio", "medio", "la mitad", "media parte")
        val nouns = denominatorNouns(d)
        val plain = nouns.map { "$numerator " + if (n > 1L) it + "s" else it }
        val feminineNumerator = SpanishNumbers.cardinal(n, Form.Feminine)
        val parte = nouns.map(::feminine).flatMap { adjective ->
            if (n > 1L) {
                listOf("$feminineNumerator ${adjective}s partes")
            } else {
                listOf("$feminineNumerator $adjective parte", "la $adjective parte")
            }
        }
        return (plain + parte).distinct()
    }

    private fun denominatorNouns(d: Long): List<String> = when (d) {
        3L -> listOf("tercio")
        11L -> listOf("onceavo", "undécimo")
        12L -> listOf("doceavo", "duodécimo")
        else -> listOfNotNull(ORDINALS[d])
    }

    /** Masculine -o to feminine -a, with tercio's irregular partner ("una tercera parte"). */
    private fun feminine(masculine: String): String =
        if (masculine == "tercio") "tercera" else masculine.dropLast(1) + "a"

    private val ORDINALS = mapOf(
        1L to "primero", 2L to "segundo", 3L to "tercero", 4L to "cuarto",
        5L to "quinto", 6L to "sexto", 7L to "séptimo", 8L to "octavo",
        9L to "noveno", 10L to "décimo", 11L to "undécimo", 12L to "duodécimo",
    )

    /**
     * A flat authored table — nothing about primero/segundo/tercero is derivable. The
     * etymological forms lead at 11 and 12, with the analogical compounds accepted (DPD).
     * The apocope primer/tercer belongs immediately before a masculine noun and a bare
     * prompt has no noun, so it is not canonical and does not grade either: where the
     * apocope goes is exactly what a Spanish ordinal drill is for.
     *
     * An n outside [LIMITS] returns nothing, which the sampler already reads as
     * "this pack cannot say that" rather than throwing across the ObjC boundary.
     */
    private fun ordinal(n: Long): List<String> {
        val forms = when (n) {
            11L -> listOf("undécimo", "decimoprimero", "décimo primero")
            12L -> listOf("duodécimo", "decimosegundo", "décimo segundo")
            else -> listOfNotNull(ORDINALS[n])
        }
        return forms.flatMap { listOf(it, feminine(it)) }
    }
}
