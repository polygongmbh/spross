package net.spross.kern.trainer

/**
 * What a question ASKS — the rule, never the rendering. Each kind names one edge of the
 * atlas: the country's own name, the language's, the person's, and the two directions of
 * the country↔language relation.
 *
 * [SpokenIn] and [SpokenWhere] are the union kinds: a country speaks several languages and
 * a language is spoken in several countries, so any of them is a correct answer and
 * [CountryDrillTask.accepted] carries them all.
 */
enum class CountryTaskKind {
    /** The country: "Deutschland" → "Ujerumani". */
    CountryName,

    /** The language: "Deutsch" → "Kijerumani". */
    LanguageName,

    /** The people: "Deutsche" → "Wajerumani" — the canonical form is the PLURAL. */
    Nationality,

    /**
     * The flag ALONE names the country asked about — no name is shown at all. It is the one
     * kind the same-name filter does not apply to ([CountryDrill]): where nothing is
     * written on the card, "Venezuela" is a question again.
     *
     * FORWARD runs only. Reversed, the answer is owed in the learner's own language, so the
     * flag would ask them to recognize their own — no rung builds this kind there.
     */
    FlagCountry,

    /** Country → a language spoken there. */
    SpokenIn,

    /** Language → a country that speaks it. */
    SpokenWhere,
}

/**
 * One atlas question. Pure data: the app shows [promptEmoji] and [promptText], takes typed
 * input, grades it against [accepted], and reveals [display] plus [gloss].
 *
 * The prompt is always in the language the learner is asked FROM and the answer in the one
 * they are asked INTO — which of the two is source and which target is the reverse flag's
 * business ([CountryDrill.sample]), settled before the task is built.
 */
data class CountryDrillTask(
    val kind: CountryTaskKind,
    /** The asked row's stable key: a country slug, or a language code. */
    val id: String,
    /**
     * The name the question is asked ABOUT — null where there is no name to show, which is
     * exactly what makes [CountryTaskKind.FlagCountry] a different question: the flag is
     * then the whole of what the learner is given.
     */
    val promptText: String?,
    /**
     * The country's flag, where the question is about one; null for a language. It is
     * carried in BOTH directions — whether it may be shown while the answer is still owed
     * is [emojiIsGiveaway]'s business, not a reason to drop the picture from the task.
     */
    val promptEmoji: String?,
    /**
     * Whether showing [promptEmoji] while ASKING would hand the learner the answer.
     *
     * True for the country questions of a REVERSED run: the answer is then owed in the
     * learner's own language, so the flag turns "name this country" into "recognize your
     * own flag" and the question stops being one. It is never true forward, and never for
     * [CountryTaskKind.FlagCountry], where the flag IS the question.
     *
     * A rule, not a rendering: the card withholds the picture while the answer is owed and
     * shows it at the reveal, because an illustration a learner never gets to see is one
     * the task might as well not have carried (`docs/design.md`).
     */
    val emojiIsGiveaway: Boolean = false,
    /** Everything graded correct, [display] first — every accepted form of every valid answer. */
    val accepted: List<String>,
    /** The canonical answer, for the reveal. */
    val display: String,
    /**
     * The reveal's second line — never shown before the answer is in. It is the answer
     * side's neighboring form: the nationality beside the country, the country beside the
     * language, so a run teaches the triple rather than one edge of it. A flag question,
     * which showed no name at all, names the country on the ASKING side instead — otherwise
     * a miss leaves the learner not knowing which country they got wrong.
     */
    val gloss: String?,
)

/**
 * What [CountryDrill.draw] hands back: the question, and the rung it actually came from —
 * a rung the run has answered out is climbed past rather than asked again.
 *
 * [task] is null exactly when the whole ladder above is answered out, which ends the run on
 * its summary rather than repeating a question.
 */
data class CountryDrillDraw(val task: CountryDrillTask?, val level: Int)

/** One country as the reference table lists it, both sides of the pair side by side. */
data class CountryReferenceRow(
    val slug: String,
    val flag: String,
    val source: String,
    val target: String,
    val sourceNationality: String,
    val targetNationality: String,
    /** The languages spoken there, as each side names them — same order, same length. */
    val sourceLanguages: List<String>,
    val targetLanguages: List<String>,
)

/**
 * The reference table, grouped by the tier a row enters the ladder at, innermost first —
 * the same grouping the rungs climb, so the table reads as the map of the drill.
 */
data class CountryReferenceGroup(val tier: Int, val rows: List<CountryReferenceRow>)
