package net.spross.kern.model

/**
 * How a realization's authored plural reads.
 *
 * The sentinels are the catalog's ("=" / "only"); the words a surface prints for each
 * ("= Pl.", "nur Pl.") are chrome and stay with the surface.
 */
sealed class PluralForm {
    /** Authored "=": the plural is the singular. */
    data object SameAsSingular : PluralForm()

    /** Authored "only": the word has no singular to teach. */
    data object PluralOnly : PluralForm()

    /** A real form, any suffix already resolved against the word. */
    data class Form(val text: String) : PluralForm()
}

/**
 * The plural [realization] carries, or null where it carries none.
 *
 * Absent and EMPTY answer the same: an authored-but-empty value is not a form,
 * and a surface that took it for one would print a bare label with nothing behind it.
 * A leading `-` is a dictionary suffix and resolves against the word
 * ("-nen" on "die Lehrerin" → "die Lehrerinnen"); anything else is the full form as authored.
 *
 * Grammar is target-side only (contract §2) — the caller passes the realization it renders.
 */
fun pluralForm(realization: Realization): PluralForm? {
    val authored = realization.grammar["plural"]?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        authored == "=" -> PluralForm.SameAsSingular
        authored == "only" -> PluralForm.PluralOnly
        authored.startsWith("-") -> PluralForm.Form(realization.text + authored.drop(1))
        else -> PluralForm.Form(authored)
    }
}

/**
 * The word's remaining family — its canonical text plus its `teaches` — minus every form in [shown].
 *
 * The exclusion is the whole point of the line: a recognition prompt rotates a synonym in,
 * so without it the reveal offers the learner the very word they are looking at as though
 * it were another one, while dropping the citation form they have not seen.
 * Empty where nothing is left to offer, which is a line the surface does not draw.
 * `accepts` never appears — it grades an answer, it does not teach a form.
 */
fun alternates(realization: Realization, shown: List<String>): List<String> =
    (listOf(realization.text) + realization.teaches).filterNot { it in shown }

/** What the card's last line says, once it has stopped asking; the label a surface puts on it is chrome. */
sealed class ClosingNote {
    /** The card's own authored note. */
    data class Own(val text: String) : ClosingNote()

    /** What the prompted form means besides this card, seed order (`session.TurnState.alsoMeans`). */
    data class AlsoMeans(val meanings: List<String>) : ClosingNote()
}

/**
 * The card's closing line, or null where it has nothing to close on.
 *
 * One line, never two: a card with something of its own to say says that,
 * and what the word also means takes the slot only where the card had nothing —
 * a second hint under the first is a line nobody reads (`docs/design.md`).
 */
fun closingNote(realization: Realization, alsoMeans: List<String>): ClosingNote? = when {
    realization.note != null -> ClosingNote.Own(realization.note)
    alsoMeans.isNotEmpty() -> ClosingNote.AlsoMeans(alsoMeans)
    else -> null
}
