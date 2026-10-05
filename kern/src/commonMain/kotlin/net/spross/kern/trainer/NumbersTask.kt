package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * One procedural drill task. Pure data — the UI compares typed input against
 * [accepted] normalize-insensitively and reveals [display].
 */
data class NumbersTask(
    val kind: NumbersReading,
    val language: Language,
    /**
     * The MACHINE form of the asked value: "347", "1978", "14:35" — never grouped,
     * never prettified. Callers parse it ([PhraseSlots] does `prompt.toLong()`, and a
     * Kotlin throw crossing the ObjC boundary is an app crash), so anything cosmetic
     * belongs in [promptDisplay] instead.
     */
    val prompt: String,
    /** All accepted answers, canonical reading first. */
    val accepted: List<String>,
    /** Canonical answer for the reveal. */
    val display: String,
    val gloss: String? = null,
    /**
     * What the UI shows — [prompt] with long runs of digits grouped ("4 072 918 300").
     * Defaults to [prompt], so a kind that must never be grouped stays ungrouped by
     * simply not setting it: that is why [Numbers.year] and [Numbers.clock] write no line for it.
     */
    val promptDisplay: String = prompt,
    /**
     * Which of the number forms this task asks, as a stable key ("negative", "decimal",
     * "percent", "multiplicative", "fraction", "ordinal", "price"); null for every other kind.
     *
     * A key, not a word: kern names the rule and the app names it in the reader's own
     * language. It exists so the first sight of a form can be introduced the way a new
     * digit length is ([Numbers.placeValueHint]) — without the app reading the mark back off the
     * prompt string, which would put the notation rule in a view.
     */
    val formKey: String? = null,
)
