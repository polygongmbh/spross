package net.spross.kern.trainer

import net.spross.kern.model.Language
import net.spross.kern.session.AnswerNormalizer

/**
 * One value a drill answer can name, distinct from every other one: the cardinal 4, the
 * ordinal 4 and the fraction 1/10 are three identities, not one number seen three ways.
 *
 * [display] is the digits the trainer already prompts that value with
 * ([TrainerTask.prompt] — "70", "11.", "1/10"), so a nudge that names the value prints
 * the prompt the learner would have seen, and no second notation is minted for it.
 */
internal sealed interface NumberIdentity {

    val display: String

    data class Cardinal(val n: Long) : NumberIdentity {
        override val display: String get() = n.toString()
    }

    /** [display] is language-dependent (the decimal mark), which is why it is carried. */
    data class Form(val value: NumberValue, override val display: String) : NumberIdentity
}

/**
 * Which values a reading names in ONE language: the answer space of the number drills,
 * turned around so a typed answer can be resolved back to what it says.
 *
 * Nothing here is authored. Every reading is the pack's own output — [TrainerLanguagePack.drillNumber]
 * for a cardinal, [TrainerLanguagePack.formReading] for a form — so a re-spelled numeral changes
 * the index the moment the pack changes, and there is no list to keep in step.
 *
 * Keyed in [AnswerNormalizer]'s comparison shape, the only shape a grader ever sees: welded and
 * hyphenated spellings collapse into one key there, so no hyphen ruling is needed and the index
 * can never disagree with the exact test it will be probed alongside.
 *
 * **Language-keyed on purpose.** Unkeyed, `dix` would resolve to 10 on an English prompt where
 * it is not a word — harmless in effect, wrong in reasoning, and a trap for the next language.
 *
 * **Only the forms that MINT vocabulary are indexed.** Negative, decimal, percent and
 * multiplicative are an invariant wrapper word around an unmodified cardinal (`menos $n`,
 * `$n percent`), so the cardinal entries already answer for them; ordinals and fractions have
 * word stock of their own.
 *
 * Built on first lookup, because the check it serves runs only on a near-miss: a run that
 * never mistypes a numeral never pays for the index at all. The build is 15–70 ms per
 * language on the JVM (es widest), which is why no range bounding was added.
 */
internal class NumberReadingIndex(
    val language: Language,
    val normalizer: AnswerNormalizer,
    private val cardinals: LongRange = DRAWN_CARDINALS,
) {

    private val byShape: Map<String, Set<NumberIdentity>> by lazy { build() }

    /** Every value read exactly as [shape] — empty where the shape names no value at all. */
    fun values(shape: String): Set<NumberIdentity> = byShape[shape].orEmpty()

    private fun build(): Map<String, Set<NumberIdentity>> {
        val pack = Trainer.pack(language)
        val index = mutableMapOf<String, MutableSet<NumberIdentity>>()
        fun put(identity: NumberIdentity, readings: List<String>) {
            for (reading in readings) {
                // The grader's own shaping, never a copy of it: verb leniency is off because
                // a drill answer is graded as a noun card ([drillGradingCard]).
                for (shape in normalizer.comparisonForms(reading, verbLeniency = false)) {
                    index.getOrPut(shape) { mutableSetOf() } += identity
                }
            }
        }
        for (n in cardinals) put(NumberIdentity.Cardinal(n), pack.drillNumber(n))
        for (value in NumberFormsAnswerSpace.drawableValues(pack.formLimits)) {
            if (value.form !in MINTING_FORMS) continue
            val identity = NumberIdentity.Form(value, renderForm(value, pack.decimalMark, grouped = false))
            put(identity, pack.formReading(value))
        }
        return index
    }

    companion object {

        /**
         * The cardinals a drill draws: [drawSampleNumber] tops out at four digits, a year at
         * [drawSampleYear]'s 2200, and the widest magnitude the forms ladder wraps is its
         * rung-10 negative below 10 000. The leveled Numbers ladder reaches ten digits
         * (`Trainer.maxLevel(Numbers)`), which no index can hold — a reading beyond this range
         * simply names no indexed value, and grades exactly as it does today.
         */
        val DRAWN_CARDINALS: LongRange = 0L..9_999L

        /** The forms with word stock of their own; the rest wrap a cardinal unchanged. */
        private val MINTING_FORMS = setOf(NumberForm.Ordinal, NumberForm.Fraction)

        private var cached: NumberReadingIndex? = null

        /**
         * The index for [language] as [normalizer] shapes it, kept across the run that asks
         * for it. One slot rather than one per language: a run drills one language, and a
         * cardinal range this wide is worth holding once, not eight times.
         */
        fun of(language: Language, normalizer: AnswerNormalizer): NumberReadingIndex =
            cached?.takeIf { it.language == language && it.normalizer === normalizer }
                ?: NumberReadingIndex(language, normalizer).also { cached = it }
    }
}
