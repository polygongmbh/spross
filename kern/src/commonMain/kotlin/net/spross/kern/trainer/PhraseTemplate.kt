package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * Slot-template model for generated phrases: a curated sentence frame whose
 * single `{slot}` is filled with a Trainer-generated value — digits on the
 * source-language prompt side, target-language words on the answer side.
 */
data class PhraseTemplate(
    val id: String,
    /** Known-language side of the frame (the prompt on forward drills). */
    val source: Language,
    /** Learning-language side; also selects the slot generator. */
    val target: Language,
    /**
     * Source sentence with `{slot}`; the prompt substitutes digits
     * ("14:35", "347", "1978"), never words.
     */
    val sourceTemplate: String,
    /**
     * Target sentence with `{slot}` (and `{count}` iff [countForms] is set);
     * display/accepted substitute the Trainer's word forms.
     */
    val targetTemplate: String,
    val slotKind: TrainerKind,
    /**
     * Accept-only alternate renderings of [targetTemplate] (the du/Sie register split):
     * graded as correct, never displayed.
     */
    val acceptedFrames: List<String> = emptyList(),
    /** [acceptedFrames] on the source side — what the reverse drill also grades as correct. */
    val acceptedSourceFrames: List<String> = emptyList(),
    /**
     * The answer realization's note in the learner's explanation language,
     * merged with the slot task's gloss.
     */
    val note: String? = null,
    /**
     * Present only on Numbers templates whose target noun must agree with the
     * numeral (Ukrainian). [targetTemplate] then contains `{count}`.
     */
    val countForms: CountForms? = null,
    /**
     * Templates counting a masculine/indeclinable noun: feminine numeral
     * variants (одна/дві) must NOT be accepted — these templates exist to
     * train exactly that agreement (language-review finding).
     */
    val masculineNumeral: Boolean = false,
) {
    /** Effective masculine-numeral rule — implied for all [countForms] templates. */
    val masculineNumeralOnly: Boolean get() = masculineNumeral || countForms != null

    /**
     * Ukrainian counted-noun agreement for a `{count}` marker following the
     * numeral: 1/21/31… → [one], 2–4/22–24… → [few], everything else
     * (incl. 11–14) → [many].
     */
    data class CountForms(val one: String, val few: String, val many: String) {
        fun form(n: Long): String {
            val lastTwo = (if (n < 0) -n else n) % 100
            if (lastTwo in 11..14) return many
            return when (lastTwo % 10) {
                1L -> one
                2L, 3L, 4L -> few
                else -> many
            }
        }
    }

    companion object {
        const val SLOT_MARKER = "{slot}"
        const val COUNT_MARKER = "{count}"
    }
}
