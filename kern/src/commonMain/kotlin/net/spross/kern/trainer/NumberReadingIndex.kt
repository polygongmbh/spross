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
 * **Every drawable form kind is indexed except Decimal.** Most non-ordinal forms are an
 * invariant wrapper word around an unmodified cardinal (`menos $n`, `$n percent`), which
 * the cardinal entries alone would answer for — but not all: Esperanto welds its
 * multiplicative (`sesfoje`), so wrapping kinds stay in. Decimal is the one kind that
 * CANNOT weld — every language speaks its separator as a word — and its digit tails are
 * cardinals below 100 the index already holds, so its thousand-value space would buy
 * nothing but build time on the oldest phone this app supports.
 *
 * Built on first lookup, because the check it serves runs only on a miss: a run that
 * never misses a numeral never pays for the index at all.
 */
internal class NumberReadingIndex(
    val language: Language,
    val normalizer: AnswerNormalizer,
    private val cardinals: List<Long> = INDEXED_CARDINALS,
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
            if (value is NumberValue.Decimal) continue
            val identity = NumberIdentity.Form(value, renderForm(value, pack.decimalMark, grouped = false))
            put(identity, pack.formReading(value))
        }
        return index
    }

    companion object {

        /**
         * The cardinals worth indexing: every known one-slip twin is a value below 120
         * (it `ventotto`/`centotto` is 28/108) or a welded round hundred
         * (eo `sescent`/`sepcent` is 600/700), so the index stops there. A compound
         * above the band is caught by the positional probe through the small word it
         * differs in — the nudge then names that word's value ("setenta" is 70) rather
         * than the compound's own. Bounded on purpose: indexing the full four-digit
         * drawn range costs a 10 000-value build on the main thread of the oldest
         * phone this app supports, for no twin anyone has found up there.
         */
        val INDEXED_CARDINALS: List<Long> = (0L..120L) + (200L..1000L step 100L)

        private var cached: NumberReadingIndex? = null

        /**
         * The index for [language] as [normalizer] shapes it, kept across the run that asks
         * for it. One slot rather than one per language: a run drills one language.
         */
        fun of(language: Language, normalizer: AnswerNormalizer): NumberReadingIndex =
            cached?.takeIf { it.language == language && it.normalizer === normalizer }
                ?: NumberReadingIndex(language, normalizer).also { cached = it }
    }
}
