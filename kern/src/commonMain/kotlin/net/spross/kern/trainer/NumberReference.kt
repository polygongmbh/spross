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
 * - `base` — everything a learner memorizes outright, 0–15: the atoms plus the teens
 *   every pack authors as a list, which is where the stems that no rule predicts live
 *   (elf, zwölf, once, quince, fifteen). 0 appears nowhere else above level 1.
 * - `tens` — the successor to Swahili's tens look-up, now offered to every language
 *   (uk сорок, de the only ß, en forty losing the u of four).
 * - `irregulars` — 16–30, the run where composition starts, offered ONLY to the
 *   languages that do not compose it plainly (see [readsSixteenToThirtyPlainly]):
 *   es welds it twice over, de and uk clip the stem (sechzehn, шістнадцять).
 *   en and sw build the whole band out of parts already shown, and get no rows for it.
 * - `compounds` — three rows proving the rule survives that band;
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
private const val IRREGULARS = "irregulars"

private val REFERENCE_VALUES: List<Pair<String, List<Long>>> = listOf(
    "base" to (0L..15L).toList(),
    "tens" to (2L..9L).map { it * 10 },
    IRREGULARS to (16L..30L).toList(),
    "compounds" to listOf(31L, 45L, 99L),
    "hundreds" to listOf(100L, 101L) + (2L..9L).map { it * 100 },
    "places" to listOf(1_000L, 2_000L, 5_000L, 1_000_000L, 2_000_000L, 1_000_000_000L),
)

internal fun buildReference(language: Language): List<ReferenceSection> {
    val pack = Trainer.pack(language)
    val bands = REFERENCE_VALUES.filterNot { (key, _) ->
        key == IRREGULARS && readsSixteenToThirtyPlainly(pack)
    }
    val cardinals = bands.map { (key, values) ->
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
 * Whether 16–30 is nothing but what the language's own composition already predicts —
 * the question that decides if the band is shown at all. Asked of the readings, never
 * declared by a pack, so a language cannot claim regularity the generator contradicts.
 *
 * A value is predicted when SOME sibling built the same way yields it once the one part
 * that differs is swapped in: a teen from another teen with its unit word exchanged, a
 * twenty from a higher decade with its tens word exchanged. Some sibling, not a chosen
 * one, because the model may be the irregular member itself (es catorce, de siebzehn) —
 * and a language is only asked about its own words, never about "ten" plus "six": the
 * teen suffix is bound everywhere, and English reads 18 as eight + -teen either way.
 *
 * A reading no sibling can even be built from counts as unpredicted: an irregularity too
 * deep to model is still an irregularity.
 */
private fun readsSixteenToThirtyPlainly(pack: TrainerLanguagePack): Boolean {
    val teens = (16L..19L).all { n ->
        (13L..19L).filter { it != n }.any { model ->
            pack.reads(model).swapping(pack.reads(model % 10), pack.reads(n % 10))
                .seamless() == pack.reads(n).seamless()
        }
    }
    val twenties = (21L..29L).all { n ->
        (30L..90L step 10).any { decade ->
            pack.reads(decade + n % 10).swapping(pack.reads(decade), pack.reads(20))
                .seamless() == pack.reads(n).seamless()
        }
    }
    return teens && twenties
}

/** The reading the drill would grade — the same one the table prints. */
private fun TrainerLanguagePack.reads(n: Long): String = number(n).first()

/** Null where [from] does not occur, so an unmodellable reading matches nothing. */
private fun String.swapping(from: String, to: String): String? =
    if (contains(from)) replace(from, to) else null

/**
 * A letter the seam writes twice, written once: "eight" + "teen" is spelled "eighteen",
 * and a learner who can spell both parts has not met an irregular number. Applied to both
 * sides of every comparison, so it can only forgive the join, never a different word.
 */
private fun String?.seamless(): String? =
    this?.filterIndexed { i, c -> i == 0 || c != this[i - 1] }

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
 * What this language ADDS for a form, in the language itself: "minus", "Komma",
 * "por ciento", "mara". Derived, never authored — the worked example's reading with the
 * cardinal's own words taken out of it, including where the cardinal is welded to the
 * front of one ("dreimal" → "mal").
 *
 * Where nothing can be taken out, the whole reading stands, and that is the honest
 * answer rather than a failure: an ordinal IS its own word ("erste", "primero"), and a
 * half is not the cardinal one with something added ("ein halb", "nusu"). Either way the
 * learner is handed target-language material, which naming the category never does.
 *
 * Returns null for a form this language does not read.
 */
internal fun formMarker(language: Language, form: NumberForm): String? {
    val pack = Trainer.pack(language)
    val value = formExamples(pack.formLimits).firstOrNull { it.form == form } ?: return null
    val reading = pack.formReading(value).firstOrNull() ?: return null
    val cardinals = value.components
        .flatMap { pack.number(it).firstOrNull()?.words().orEmpty() }
        .map { it.lowercase() }
        .toSet()
    // why: matched case-insensitively but emitted as written — German capitalizes the
    // very words this returns ("Komma", "Prozent"), and a hint is shown, not compared.
    val kept = reading.words().mapNotNull { word ->
        val lower = word.lowercase()
        when {
            lower in cardinals -> null
            else -> cardinals.firstOrNull { lower.length > it.length && lower.startsWith(it) }
                ?.let { word.drop(it.length) } ?: word
        }
    }
    return kept.joinToString(" ").ifBlank { reading }
}

/** Words of a reading, stripped of the marks a reading can carry. */
private fun String.words(): List<String> =
    split(' ', '-').map { it.trim(',', '.') }.filter { it.isNotBlank() }

/** The plain numbers a form is built from — what its reading says besides the mark. */
private val NumberValue.components: List<Long>
    get() = when (this) {
        is NumberValue.Negative -> listOf(magnitude)
        is NumberValue.Decimal -> listOf(whole) + fractionDigits.map { it.digitToInt().toLong() }
        is NumberValue.Percent -> listOf(n)
        is NumberValue.Multiplicative -> listOf(n)
        is NumberValue.Fraction -> listOf(numerator, denominator)
        is NumberValue.Ordinal -> listOf(n)
    }

/**
 * The lowest ordinal the language ranks — 1 wherever it is offered, since that is where
 * the irregular stems live ("erste", "primero", "перший").
 */
private fun ordinalExample(limits: FormLimits): NumberValue.Ordinal? {
    val first = maxOf(1L, limits.ordinalRange.first)
    return if (first > limits.ordinalRange.last) null else NumberValue.Ordinal(first)
}
