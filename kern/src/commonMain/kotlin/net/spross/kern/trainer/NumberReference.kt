package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * One row of the reference table: the written value and how the language reads it.
 * [value] is grouped the same way a prompt is ("1 000 000"), so the page and the
 * drill write a number identically.
 */
data class ReferenceEntry(val value: String, val reading: String)

/**
 * A band of the reference table. [key] is a stable identifier the app localizes into
 * a heading — kern names the rule, never the rendering, so no chrome string lives here.
 */
data class ReferenceSection(val key: String, val entries: List<ReferenceEntry>)

/**
 * Which numbers a reference page shows. Authored as values only: every reading is
 * generated from the same packs the drill asks from, so the table cannot drift from
 * what a learner is graded against.
 *
 * The bands follow where the languages are actually irregular, not the number line:
 * - `ones` — the atoms, 0 included because it appears nowhere else above level 1.
 * - `teens` — 10 sits here, not in `tens`, because every pack but Swahili defines
 *   10–19 as one list; it is the most irregular band in four of five languages.
 * - `tens` — the successor to Swahili's tens look-up, now offered to every language
 *   (uk сорок, de the only ß, en forty losing the u of four).
 * - `twenties` — the combination rule as a run: Spanish welds a second time here and
 *   German's reversal appears nine times over, which is what makes it stick.
 * - `compounds` — three rows proving the rule survives the irregular twenties;
 *   load-bearing for es, where 31 unwelds into "treinta y uno".
 * - `hundreds` — the irregular es stems and the uk -сот series, plus 101, the one
 *   non-round value: es switches cien→ciento there and de yields "einhunderteins".
 * - `places` — the scale WORD and the scale AGREEMENT are different facts, and only
 *   the second is hard: 1000/2000/5000 renders the Slavic three-way, 2000000 is where
 *   de and es pluralize, 10^9 is where es leaves the short scale. 10^4 and 10^5 are
 *   omitted because every pack builds them as a plain multiple of the thousand row.
 *
 * - `forms` — one worked example per form the language reads, in ladder order, so the
 *   notation a Forms run asks about (the minus, the decimal mark, the percent sign, the
 *   fraction bar, the ordinal dot) is written down somewhere other than a failed task.
 *   A form the language cannot read has no row: the same reach that keeps it out of the
 *   drill keeps it off the page.
 */
private val REFERENCE_VALUES: List<Pair<String, List<Long>>> = listOf(
    "ones" to (0L..9L).toList(),
    "teens" to (10L..19L).toList(),
    "tens" to (2L..9L).map { it * 10 },
    "twenties" to (21L..29L).toList(),
    "compounds" to listOf(31L, 45L, 99L),
    "hundreds" to listOf(100L, 101L) + (2L..9L).map { it * 100 },
    "places" to listOf(1_000L, 2_000L, 5_000L, 1_000_000L, 2_000_000L, 1_000_000_000L),
)

internal fun buildReference(language: Language): List<ReferenceSection> {
    val pack = Trainer.pack(language)
    val cardinals = REFERENCE_VALUES.map { (key, values) ->
        ReferenceSection(
            key,
            values.map { ReferenceEntry(groupDigits(it.toString()), pack.number(it).first()) },
        )
    }
    val forms = formExamples(pack.formLimits).mapNotNull { value ->
        val reading = pack.formReading(value).firstOrNull() ?: return@mapNotNull null
        ReferenceEntry(renderForm(value, pack.decimalMark, grouped = true), reading)
    }
    return if (forms.isEmpty()) cardinals else cardinals + ReferenceSection("forms", forms)
}

/**
 * One example per form the language reads, in ladder order — the smallest value that
 * shows the NOTATION rather than a hard number, because the row exists to say what the
 * mark means, not to test the reading. The fraction and the ordinal take theirs from the
 * language's own pool: a pack that starts its fractions at thirds must not be shown a half
 * it would never ask for.
 */
private fun formExamples(limits: FormLimits): List<NumberValue> =
    NumberForm.entries.filter { it in limits.forms }.mapNotNull { form ->
        when (form) {
            NumberForm.Negative -> NumberValue.Negative(7)
            NumberForm.Decimal -> NumberValue.Decimal(3, "5")
            NumberForm.Percent -> NumberValue.Percent(25)
            NumberForm.Multiplicative -> NumberValue.Multiplicative(3)
            NumberForm.Fraction -> fractionPool(limits, wide = false).firstOrNull()
            NumberForm.Ordinal -> ordinalExample(limits)
        }
    }

/**
 * The lowest ordinal the language ranks — 1 wherever it is offered, since that is where
 * the irregular stems live ("erste", "primero", "перший").
 */
private fun ordinalExample(limits: FormLimits): NumberValue.Ordinal? {
    val first = maxOf(1L, limits.ordinalRange.first)
    return if (first > limits.ordinalRange.last) null else NumberValue.Ordinal(first)
}
