package net.spross.kern.model

/**
 * One composable exercise: a card in one role, for recognize additionally one
 * synonym-class form of the target realization.
 */
data class ExerciseUnit(
    val card: Card,
    val role: Role,
    /** Raw target form (display shape) for recognize; null for produce. */
    val form: String? = null,
    /** Catalog order of the form: canonical text 0, synonyms 1… (0 for produce). */
    val formIndex: Int = 0,
) {
    val key: String
        get() = when (role) {
            Role.Produce -> UnitKey.produce(card.id).encoded
            Role.Recognize -> UnitKey.recognize(card.id, form!!).encoded
        }
}

object ExerciseUnits {
    /**
     * Expands a card into its scheduling units:
     * produce always; recognize per synonym-class form (canonical text + each synonym),
     * nouns/verbs only, NEVER phrases. Variants are grading/display-only — no units.
     */
    fun of(card: Card): List<ExerciseUnit> {
        val units = mutableListOf(ExerciseUnit(card, Role.Produce))
        if (card.kind != CardKind.Phrase) {
            (listOf(card.target.text) + card.target.synonyms).forEachIndexed { index, form ->
                units += ExerciseUnit(card, Role.Recognize, form, index)
            }
        }
        return units
    }

    /** Fully pinned unit order — map iteration order never leaks. */
    val order: Comparator<ExerciseUnit> = compareBy(
        { it.card.seedIndex },
        { it.card.id },
        { it.role.rank },
        { it.formIndex },
    )
}
