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

    override fun toString(): String =
        FormDimension.entries.mapNotNull { values[it] }.joinToString(".")

    companion object {
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

/** One inflected form of a realization, beside its citation `text`. */
data class TaggedForm(val tag: FormTag, val text: String)
