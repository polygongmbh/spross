package net.spross.kern.trainer

import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * Which atlas entry a typed country-drill answer exactly names — the atlas turned around,
 * so a slip the typo budget forgave can be refused when it is really ANOTHER entry's name
 * (eo `Ĉilio` for `Ĉinio`, sw `Uswidi` for `Uswisi`).
 *
 * Scoped to ONE kind at a time: countries against countries, peoples against peoples,
 * languages against languages. A country and its nationality are two rungs asking two
 * questions, never one accepted set — de `Spanien`/`Spanier` is a near-miss to forgive,
 * not a confusion to refuse — so a scope may never see another scope's names.
 *
 * Exactness is the whole test, as in `CatalogAnswerGrader`: only the answer-side forms an
 * entry itself accepts count, keyed by the normalizer's own comparison shape, so the check
 * re-labels a miss and never widens what counts as wrong. The named meaning is the entry's
 * PROMPT-side name — the one the learner would have been asked with.
 */
internal class CountryNameIndex(
    content: CountryDrillContent,
    reverse: Boolean,
    private val normalizer: AnswerNormalizer,
) {

    /** The three answer spaces a task's kind can draw from. */
    enum class Scope { Countries, Peoples, Languages }

    private val byScope: Map<Scope, Map<String, List<String>>> by lazy {
        val countries = mutableMapOf<String, MutableList<String>>()
        val peoples = mutableMapOf<String, MutableList<String>>()
        val languages = mutableMapOf<String, MutableList<String>>()
        fun put(into: MutableMap<String, MutableList<String>>, forms: List<String>, meaning: String) {
            for (form in forms) {
                for (shape in normalizer.comparisonForms(form, verbLeniency = false)) {
                    val meanings = into.getOrPut(shape) { mutableListOf() }
                    if (meaning !in meanings) meanings += meaning
                }
            }
        }
        for (entry in content.countries) {
            val answer = if (reverse) entry.source else entry.target
            val prompt = if (reverse) entry.target else entry.source
            put(countries, listOf(answer.text) + answer.variants, prompt.text)
            put(
                peoples,
                listOf(answer.nationality.text) + answer.nationality.variants,
                prompt.nationality.text,
            )
        }
        for (entry in content.languages) {
            val answer = if (reverse) entry.source else entry.target
            val prompt = if (reverse) entry.target else entry.source
            put(languages, listOf(answer.name) + answer.variants, prompt.name)
        }
        mapOf(Scope.Countries to countries, Scope.Peoples to peoples, Scope.Languages to languages)
    }

    /**
     * The entry [typed] names whole in [kind]'s own scope, or null. Consulted on every
     * miss, never on Exact — so an accepted answer can never reach it and the asked entry
     * can never own the probe.
     */
    fun otherName(kind: CountryTaskKind, typed: String): Match.OtherWord? {
        val scope = when (kind) {
            CountryTaskKind.CountryName, CountryTaskKind.FlagCountry, CountryTaskKind.SpokenWhere ->
                Scope.Countries
            CountryTaskKind.Nationality -> Scope.Peoples
            CountryTaskKind.LanguageName, CountryTaskKind.SpokenIn -> Scope.Languages
        }
        val shape = normalizer.comparisonForms(typed, verbLeniency = false).firstOrNull() ?: return null
        val meanings = byScope.getValue(scope)[shape] ?: return null
        return Match.OtherWord(word = typed.trim(), meanings = meanings)
    }
}
