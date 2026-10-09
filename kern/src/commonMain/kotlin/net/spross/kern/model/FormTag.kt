package net.spross.kern.model

/** An axis a word inflects along. A tag names at most one value per dimension. */
enum class FormDimension { Gender, Number, Case, NounClass }

/**
 * Which inflected form a [TaggedForm] is, as dimension values (`f`, `pl`, `f.dat`, sw class `7`).
 * One vocabulary across every language, so the same tag on two sides names the same form.
 * A dimension the tag leaves out is the citation's value on it (`f` is feminine singular nominative).
 */
data class FormTag(val values: Map<FormDimension, String>) {
    val dimensions: Set<FormDimension> get() = values.keys

    /** The values in dimension order, as a marker reads them (`f`, `pl`). */
    val parts: List<String> get() = FormDimension.entries.mapNotNull { values[it] }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        val FEMININE = FormTag(mapOf(FormDimension.Gender to "f"))
        val PLURAL = FormTag(mapOf(FormDimension.Number to "pl"))

        private val VOCABULARY: Map<String, FormDimension> =
            listOf("m", "f", "n").associateWith { FormDimension.Gender } +
                mapOf("pl" to FormDimension.Number) +
                listOf("nom", "gen", "dat", "acc", "ins", "loc", "voc").associateWith { FormDimension.Case } +
                (1..18).associate { it.toString() to FormDimension.NounClass }

        /** Null where [tag] names an unknown value or one dimension twice. */
        fun parse(tag: String): FormTag? {
            val values = mutableMapOf<FormDimension, String>()
            for (part in tag.split('.')) {
                val dimension = VOCABULARY[part] ?: return null
                if (values.put(dimension, part) != null) return null
            }
            return FormTag(values)
        }
    }
}

/**
 * The glyph a marker writes for a gender value (`f` → ♀), null for every other part —
 * those are the grammar's abbreviations, which each app words from its string table.
 * Kern's for the reason [kindEmoji] is: a map two platforms each keep ends up disagreeing.
 */
fun formGlyph(part: String): String? = when (part) {
    "f" -> "♀"
    "m" -> "♂"
    "n" -> "⚲"
    else -> null
}

/**
 * One inflected form of a realization, beside its citation `text`.
 * Authored with its article in front (`die Lehrerin`); the join sets that apart as [article],
 * so [text] is the bare word a card shows and a recording says.
 */
data class TaggedForm(val tag: FormTag, val text: String, val article: String? = null) {
    /** The form as written, its article in front — what an answer is graded against. */
    val written: String get() = articledForm(article, text)
}

/** A form offered beside the card's own, with the marker that says which form it is; null marks none. */
data class Alternate(val text: String, val marker: FormTag?)
