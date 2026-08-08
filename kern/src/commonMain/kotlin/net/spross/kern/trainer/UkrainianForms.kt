package net.spross.kern.trainer

/**
 * Ukrainian readings of the written number forms
 * (Український правопис 2019 §107 · ДВНЗ УДХТУ «Числівник» · НУШ maths · СУМ-11 · goroh.pp.ua).
 *
 * Every form ships; the real limits are agreement rules, not exclusions.
 * Two of them decide most of this file:
 * the decimal's whole part and every fraction numerator are FEMININE
 * (the head nouns ціла and частина are), and everything counted goes through
 * [UkrainianNumbers.agree] — the pack's single agreement device.
 *
 * There is no "кома" register. German reads 3,5 as "drei Komma fünf" and Ukrainian does
 * not: кома is the NAME of the punctuation mark, and even the uk.wikipedia article about
 * the comma reads its own example «п'ять цілих вісім десятих». The цілих/десятих reading
 * is the only one, so it is the only one that grades.
 */
internal object UkrainianForms {

    val LIMITS = FormLimits(forms = NumberForm.entries.toSet())

    fun reading(value: NumberValue): List<String> = when (value) {
        is NumberValue.Negative -> UkrainianNumbers.variants(value.magnitude).map { "мінус $it" }
        is NumberValue.Decimal -> decimal(value.whole, value.fractionDigits)
        is NumberValue.Percent -> counted(value.n, PERCENT_NOUNS)
        is NumberValue.Multiplicative -> multiplicative(value.n)
        is NumberValue.Fraction -> fraction(value.numerator, value.denominator)
        is NumberValue.Ordinal -> ordinal(value.n)
    }

    /**
     * Whole part + ціла/цілих, then the fraction digits read as one feminine numeral
     * carrying the place name of 10^length. The place comes from the STRING LENGTH,
     * so a leading zero survives without a special case: 3,05 is "три цілих п'ять сотих"
     * and 3,40 is "три цілих сорок сотих".
     *
     * School materials print an "і" between the halves, so that grades too.
     */
    private fun decimal(whole: Long, fractionDigits: String): List<String> {
        val places = PLACES[fractionDigits.length] ?: return emptyList()
        val head = UkrainianNumbers.feminine(whole) + " " +
            UkrainianNumbers.agree(whole, "ціла", "цілих", "цілих")
        val numerator = fractionDigits.toLong()
        val tails = listOf(
            UkrainianNumbers.agree(numerator, places[0], places[2], places[2]),
            // The 2007 norm's nominative plural for numerators 2–4, still widely printed.
            UkrainianNumbers.agree(numerator, places[0], places[1], places[2]),
        ).distinct().map { UkrainianNumbers.feminine(numerator) + " " + it }
        return listOf("", "і ").flatMap { link -> tails.map { "$head $link$it" } }.distinct()
    }

    /** Feminine nominative singular · nominative plural · genitive plural, by digit count. */
    private val PLACES = mapOf(
        1 to listOf("десята", "десяті", "десятих"),
        2 to listOf("сота", "соті", "сотих"),
        3 to listOf("тисячна", "тисячні", "тисячних"),
    )

    /** відсоток is native and canonical; процент is a normative synonym of the technical register. */
    private val PERCENT_NOUNS = listOf(
        listOf("відсоток", "відсотки", "відсотків"),
        listOf("процент", "проценти", "процентів"),
    )

    /**
     * MASCULINE cardinal plus the counted noun — відсоток and раз are masculine, so the
     * feminine variants the cardinal generator offers must not be mapped over blindly
     * ("одна відсоток" is not a reading).
     */
    private fun counted(n: Long, nouns: List<List<String>>): List<String> =
        nouns.map { UkrainianNumbers.cardinal(n) + " " + UkrainianNumbers.agree(n, it[0], it[1], it[2]) }

    /**
     * The noun phrase is canonical because it is the only pattern that reaches every n;
     * двічі and тричі exist for 2 and 3 alone and are accepted there.
     *
     * удвічі/утричі/учетверо are NOT accepted: they mean twofold/threefold, a factor rather
     * than a count of occasions, and grading them here would teach the conflation. Neither is
     * "раза", whose genitive singular belongs after півтора and after fractional quantities.
     */
    private fun multiplicative(n: Long): List<String> {
        val readings = counted(n, MULTIPLICATIVE_NOUNS).toMutableList()
        when (n) {
            1L -> readings += "раз"
            2L -> readings += "двічі"
            3L -> readings += "тричі"
        }
        return readings
    }

    private val MULTIPLICATIVE_NOUNS = listOf(listOf("раз", "рази", "разів"))

    /**
     * Feminine numerator (the elided head is частина) plus the denominator's feminine
     * ordinal: nominative singular after 1, genitive plural after everything else —
     * "одна друга", "дві третіх". The 2007 edition of the правопис put numerators 2–4 in
     * the NOMINATIVE plural ("дві треті") and that text is still in wide circulation, so
     * it grades; the current edition decides which one is shown.
     *
     * половина/третина/чверть are accepted BARE only: the -ин suffix already carries the
     * singularity, which is why "одна третина" is wrong. Bare "пів" never grades — §36
     * makes it an indeclinable numeral that requires a following genitive noun.
     */
    private fun fraction(n: Long, d: Long): List<String> {
        val forms = DENOMINATORS[d.toInt()] ?: return emptyList()
        val numerator = UkrainianNumbers.feminine(n)
        val readings = listOf(
            UkrainianNumbers.agree(n, forms[0], forms[2], forms[2]),
            UkrainianNumbers.agree(n, forms[0], forms[1], forms[2]),
        ).distinct().map { "$numerator $it" }.toMutableList()
        if (n == 1L) UNIT_NOUNS[d.toInt()]?.let { readings += it }
        return readings
    }

    /** Feminine nominative singular · nominative plural · genitive plural. */
    private val DENOMINATORS = mapOf(
        2 to listOf("друга", "другі", "других"),
        3 to listOf("третя", "треті", "третіх"),
        4 to listOf("четверта", "четверті", "четвертих"),
        5 to listOf("п'ята", "п'яті", "п'ятих"),
        6 to listOf("шоста", "шості", "шостих"),
        7 to listOf("сьома", "сьомі", "сьомих"),
        8 to listOf("восьма", "восьмі", "восьмих"),
        9 to listOf("дев'ята", "дев'яті", "дев'ятих"),
        10 to listOf("десята", "десяті", "десятих"),
        11 to listOf("одинадцята", "одинадцяті", "одинадцятих"),
        12 to listOf("дванадцята", "дванадцяті", "дванадцятих"),
    )

    private val UNIT_NOUNS = mapOf(2 to "половина", 3 to "третина", 4 to "чверть")

    /**
     * Only the LAST word of a compound becomes ordinal, so the map is keyed by cardinal
     * word and 21 falls out as "двадцять перший". Masculine nominative singular is
     * canonical — it is Ukrainian's citation form and the pack's existing convention —
     * with feminine and neuter accepted; the plural would need a plural noun the bare
     * prompt does not supply.
     */
    private fun ordinal(n: Long): List<String> {
        val cardinal = UkrainianNumbers.cardinal(n)
        val cut = cardinal.lastIndexOf(' ') + 1
        val last = ORDINALS[cardinal.substring(cut)] ?: return emptyList()
        return genders(cardinal.substring(0, cut) + last)
    }

    /** третій is a soft adjective (третя/третє); every other ordinal takes -а/-е. */
    private fun genders(masculine: String): List<String> {
        val stem = masculine.dropLast(2)
        return if (masculine.endsWith("ій")) {
            listOf(masculine, stem + "я", stem + "є")
        } else {
            listOf(masculine, stem + "а", stem + "е")
        }
    }

    private val ORDINALS = mapOf(
        "один" to "перший", "два" to "другий", "три" to "третій", "чотири" to "четвертий",
        "п'ять" to "п'ятий", "шість" to "шостий", "сім" to "сьомий", "вісім" to "восьмий",
        "дев'ять" to "дев'ятий", "десять" to "десятий", "одинадцять" to "одинадцятий",
        "дванадцять" to "дванадцятий", "тринадцять" to "тринадцятий",
        "чотирнадцять" to "чотирнадцятий", "п'ятнадцять" to "п'ятнадцятий",
        "шістнадцять" to "шістнадцятий", "сімнадцять" to "сімнадцятий",
        "вісімнадцять" to "вісімнадцятий", "дев'ятнадцять" to "дев'ятнадцятий",
        "двадцять" to "двадцятий", "тридцять" to "тридцятий", "сорок" to "сороковий",
        "п'ятдесят" to "п'ятдесятий", "шістдесят" to "шістдесятий", "сімдесят" to "сімдесятий",
        "вісімдесят" to "вісімдесятий", "дев'яносто" to "дев'яностий", "сто" to "сотий",
    )
}
