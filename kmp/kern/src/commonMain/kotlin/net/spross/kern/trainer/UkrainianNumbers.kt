package net.spross.kern.trainer

/**
 * Ukrainian cardinal numbers. Canonical form uses masculine counting words
 * (один, два); feminine forms (одна, дві) are produced as accepted variants.
 * Thousands always take the grammatically required feminine multiplier plus
 * тисяча/тисячі/тисяч agreement (одна тисяча, дві тисячі, п'ять тисяч).
 */
internal object UkrainianNumbers {
    private val onesMasc = listOf("", "один", "два", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять")
    private val onesFem = listOf("", "одна", "дві", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять")
    private val teens = listOf("десять", "одинадцять", "дванадцять", "тринадцять", "чотирнадцять", "п'ятнадцять", "шістнадцять", "сімнадцять", "вісімнадцять", "дев'ятнадцять")
    private val tens = listOf("", "", "двадцять", "тридцять", "сорок", "п'ятдесят", "шістдесят", "сімдесят", "вісімдесят", "дев'яносто")
    private val hundreds = listOf("", "сто", "двісті", "триста", "чотириста", "п'ятсот", "шістсот", "сімсот", "вісімсот", "дев'ятсот")

    /** Words for 1..999 (space-joined). [feminine] switches 1/2 to одна/дві. */
    private fun subThousand(n: Int, feminine: Boolean): List<String> {
        val words = mutableListOf<String>()
        var rest = n
        if (rest >= 100) {
            words += hundreds[rest / 100]
            rest %= 100
        }
        if (rest >= 20) {
            words += tens[rest / 10]
            rest %= 10
        } else if (rest >= 10) {
            words += teens[rest - 10]
            rest = 0
        }
        if (rest > 0) {
            words += if (feminine) onesFem[rest] else onesMasc[rest]
        }
        return words
    }

    /**
     * Slavic count agreement for a multiplier [t]: form for a bare 1, forms
     * for 2–4, else the "many" (genitive plural) form; the 11–14 exception
     * always takes the "many" form.
     */
    private fun agree(t: Long, one: String, few: String, many: String): String {
        if ((t % 100) in 11..14) return many
        return when ((t % 10).toInt()) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }

    private fun compose(n: Long, feminineUnits: Boolean): String {
        if (n < 0) return n.toString()
        if (n == 0L) return "нуль"
        if (n < 1000) return subThousand(n.toInt(), feminineUnits).joinToString(" ")
        if (n / 1_000_000_000 > 9) return n.toString()
        val words = mutableListOf<String>()
        var rest = n
        // Millions/billions count with MASCULINE multipliers (один мільйон,
        // два мільйони); only тисяча takes the feminine multiplier.
        val billions = rest / 1_000_000_000
        rest %= 1_000_000_000
        if (billions > 0) {
            words += subThousand(billions.toInt(), feminine = false)
            words += agree(billions, "мільярд", "мільярди", "мільярдів")
        }
        val millions = rest / 1_000_000
        rest %= 1_000_000
        if (millions > 0) {
            words += subThousand(millions.toInt(), feminine = false)
            words += agree(millions, "мільйон", "мільйони", "мільйонів")
        }
        val thousands = rest / 1000
        rest %= 1000
        if (thousands > 0) {
            // why: the multiplier before тисяча is always feminine (одна тисяча, дві тисячі)
            words += subThousand(thousands.toInt(), feminine = true)
            words += agree(thousands, "тисяча", "тисячі", "тисяч")
        }
        if (rest > 0) {
            words += subThousand(rest.toInt(), feminineUnits)
        }
        return words.joinToString(" ")
    }

    /** Canonical (masculine counting) form. */
    fun cardinal(n: Long): String = compose(n, feminineUnits = false)

    /**
     * Canonical form first, then accepted variants: feminine unit ending
     * (одна/дві) and, for 1xxx numbers, the common reading without leading
     * "одна" ("тисяча дев'ятсот …").
     */
    fun variants(n: Long): List<String> {
        val list = mutableListOf(cardinal(n))
        val feminine = compose(n, feminineUnits = true)
        if (feminine != list[0]) list += feminine
        if (n in 1000..1999) {
            for (form in listOf(list[0], feminine)) {
                if (form.startsWith("одна тисяча")) {
                    val short = form.removePrefix("одна ")
                    if (short !in list) list += short
                }
            }
        }
        return list.distinct()
    }
}
