package net.spross.kern.trainer

import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.DateEntry
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.Match

/**
 * Which calendar name a typed bare-name answer exactly spells — the calendar turned
 * around, [CountryNameIndex]'s shape, so a slip the typo budget forgave is refused when
 * it is really ANOTHER name: de `Juli` for `Juni` is July, not a typo of June, and the
 * refusal names it by its prompt-side name so the reveal can say what was written.
 *
 * Exactness is the whole test: only forms the calendar itself carries count, keyed by the
 * normalizer's own comparison shape, so the check re-labels a miss and never widens what
 * counts as wrong. `dateForm` is indexed although the bare Sprosse does not accept it —
 * inside a date it is that name's word, and typed bare it still names that name.
 *
 * The asked entry is skipped by key rather than by its accepted forms: its `dateForm`
 * sits in the index and NOT in the bare Sprosse's accepted set (the Sprosse asks the citation
 * name), so uk `березня` typed at March must stay March's own plain miss, never a
 * refusal naming the month that was asked.
 */
internal class DateNameIndex(
    content: DateDrillContent,
    reverse: Boolean,
    private val normalizer: AnswerNormalizer,
) {

    private class Owner(val key: String, val meaning: String)

    // why: ONE merged space over all nineteen names where CountryNameIndex scopes by kind —
    // eo `mardo` (Tuesday) and `marto` (March) sit one edit apart ACROSS the weekday/month
    // line, and no calendar pair is entangled the way Spanien/Spanier are, so merging only
    // catches and never harms.
    private val owners: Map<String, List<Owner>> by lazy {
        val index = mutableMapOf<String, MutableList<Owner>>()
        fun put(kind: DateTaskKind, entries: List<DateEntry>) {
            for (entry in entries) {
                val answer = if (reverse) entry.source else entry.target
                val prompt = if (reverse) entry.target else entry.source
                val owner = Owner(key(kind, entry.index.toString()), prompt.text)
                val forms = listOf(answer.text) + answer.synonyms + answer.variants +
                    listOfNotNull(answer.dateForm)
                for (form in forms) {
                    for (shape in normalizer.comparisonForms(form, verbLeniency = false)) {
                        val holders = index.getOrPut(shape) { mutableListOf() }
                        if (holders.none { it.key == owner.key }) holders += owner
                    }
                }
            }
        }
        put(DateTaskKind.Weekday, content.weekdays)
        put(DateTaskKind.Month, content.months)
        index
    }

    /**
     * The name [typed] spells whole and [task] did not ask, or null. Consulted on a
     * bare-name miss only — never on Exact, so an accepted answer cannot reach it, and
     * never on an assembled Sprosse, where a bridged name is the forgiven typo the owner
     * ruled it to be.
     */
    fun otherName(task: DateDrillTask, typed: String): Match.OtherWord? {
        val shape = normalizer.comparisonForms(typed, verbLeniency = false).firstOrNull() ?: return null
        val asked = key(task.kind, task.id)
        val hits = owners[shape].orEmpty().filter { it.key != asked }
        if (hits.isEmpty()) return null
        return Match.OtherWord(word = typed.trim(), meanings = hits.map { it.meaning }.distinct())
    }

    private fun key(kind: DateTaskKind, id: String) = "$kind:$id"
}
