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

    /** The person: "Deutscher" → "Mjerumani". */
    Nationality,

    /**
     * The flag ALONE names the country asked about — no name is shown at all. It is the one
     * kind the same-name filter does not apply to ([CountryDrill]): where nothing is
     * written on the card, "Venezuela" is a question again.
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
    /** The country's flag, where the question is about one; null for a language. */
    val promptEmoji: String?,
    /** Everything graded correct, [display] first — every accepted form of every valid answer. */
    val accepted: List<String>,
    /** The canonical answer, for the reveal. */
    val display: String,
    /**
     * The reveal's second line — never shown before the answer is in. It is the answer
     * side's neighbouring form: the nationality beside the country, the country beside the
     * language, so a run teaches the triple rather than one edge of it. A flag question,
     * which showed no name at all, names the country on the ASKING side instead — otherwise
     * a miss leaves the learner not knowing which country they got wrong.
     */
    val gloss: String?,
)

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
