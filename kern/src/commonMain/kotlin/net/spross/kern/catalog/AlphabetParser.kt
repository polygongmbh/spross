package net.spross.kern.catalog

import kotlinx.serialization.json.JsonObject
import net.spross.kern.model.Language
import net.spross.kern.model.nfcNormalized

/**
 * `catalog/alphabet/<lang>.json` → [Alphabet], on [CatalogParser]'s conventions: unknown
 * keys rejected, every failure a [CatalogFormatException] naming the file and the row.
 *
 * Two things are derived rather than authored — [AlphabetEntry.ref] (the `id` where one
 * is declared, else the glyph) and the symmetric closure of both confusion axes.
 */
internal object AlphabetParser {
    private val ENTRY_KEYS = setOf(
        "glyph", "upper", "kind", "id", "name", "ipa",
        "example", "exampleText", "hints", "context", "drill", "confusable",
    )
    private val AXES = setOf("look", "sound")
    private val ID_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    fun parse(path: String, text: String, language: Language, declared: Set<Language>): Alphabet {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("entries"))
        val rows = root["entries"]?.arr(path, "entries") ?: parseError(path, "missing \"entries\"")
        if (rows.isEmpty()) parseError(path, "empty entries")
        val entries = rows.mapIndexed { i, el ->
            parseEntry(path, el.obj(path, "entries[$i]"), "entries[$i]", declared)
        }
        return Alphabet(language, close(path, entries))
    }

    private fun parseEntry(
        path: String,
        o: JsonObject,
        context: String,
        declared: Set<Language>,
    ): AlphabetEntry {
        o.rejectUnknownKeys(path, context, ENTRY_KEYS)
        val glyph = nfcNormalized(o.requireString(path, context, "glyph"))
        if (glyph.isBlank()) parseError(path, "$context: blank glyph")
        val where = "$context ($glyph)"
        val kind = when (val raw = o.optionalString(path, where, "kind") ?: "letter") {
            "letter" -> AlphabetKind.Letter
            "digraph" -> AlphabetKind.Digraph
            "contextual" -> AlphabetKind.Contextual
            "rule" -> AlphabetKind.Rule
            else -> parseError(path, "$where: unknown kind \"$raw\"")
        }
        // why: a prompted or tiled glyph is one writable grapheme; prose belongs to a rule row.
        if (kind != AlphabetKind.Rule && glyph.any { it.isWhitespace() }) {
            parseError(path, "$where: whitespace in a ${kind.name.lowercase()} glyph")
        }
        val id = o.nonBlank(path, where, "id")?.also {
            if (!ID_PATTERN.matches(it)) parseError(path, "$where: bad id \"$it\"")
        }
        val hints = languageMap(path, where, o, "hints", declared)
        val ipa = o.nonBlank(path, where, "ipa")
        if (ipa == null && hints.isEmpty()) parseError(path, "$where: needs an ipa or a hint")
        val confusable = o.stringListMap(path, where, "confusable")
        for (axis in confusable.keys) {
            if (axis !in AXES) parseError(path, "$where: unknown confusable axis \"$axis\"")
        }
        return AlphabetEntry(
            ref = id ?: glyph,
            glyph = glyph,
            upper = o.nonBlank(path, where, "upper"),
            kind = kind,
            name = o.nonBlank(path, where, "name"),
            ipa = ipa,
            exampleSlug = o.nonBlank(path, where, "example"),
            exampleText = o.nonBlank(path, where, "exampleText"),
            hints = hints,
            context = languageMap(path, where, o, "context", declared),
            // why: a rule row is prose the sheet renders — `drill` on one is ignored, not obeyed.
            drill = kind != AlphabetKind.Rule && (o.optionalBoolean(path, where, "drill") ?: true),
            confusableLook = confusable["look"].orEmpty(),
            confusableSound = confusable["sound"].orEmpty(),
        )
    }

    /** Identity checks, then both confusion axes resolved to refs and mirrored. */
    private fun close(path: String, entries: List<AlphabetEntry>): List<AlphabetEntry> {
        val byGlyph = entries.groupBy { it.glyph }
        for ((glyph, group) in byGlyph) {
            // why: without ids the drill cannot say WHICH ch it is asking about, and a
            // confusable ref naming the glyph would address all of them at once.
            if (group.size > 1 && group.any { it.ref == glyph }) {
                parseError(path, "glyph \"$glyph\" is authored ${group.size} times — each needs an \"id\"")
            }
        }
        val refs = entries.map { it.ref }
        refs.groupingBy { it }.eachCount().forEach { (ref, count) ->
            if (count > 1) parseError(path, "duplicate ref \"$ref\"")
        }
        val look = entries.associate { it.ref to mutableListOf<String>() }
        val sound = entries.associate { it.ref to mutableListOf<String>() }
        for (entry in entries) {
            val axes = listOf(look to entry.confusableLook, sound to entry.confusableSound)
            for ((axis, authored) in axes) {
                for (raw in authored) {
                    val target = resolve(path, entry, raw, refs.toSet(), byGlyph)
                    axis.getValue(entry.ref).addUnlessPresent(target)
                    axis.getValue(target).addUnlessPresent(entry.ref)
                }
            }
        }
        return entries.map {
            it.copy(confusableLook = look.getValue(it.ref), confusableSound = sound.getValue(it.ref))
        }
    }

    /** An `id`, or a glyph naming exactly one row; anything else is an authoring mistake. */
    private fun resolve(
        path: String,
        from: AlphabetEntry,
        raw: String,
        refs: Set<String>,
        byGlyph: Map<String, List<AlphabetEntry>>,
    ): String {
        val group = byGlyph[nfcNormalized(raw)].orEmpty()
        val ref = when {
            raw in refs -> raw
            group.isEmpty() -> parseError(path, "${from.ref}: confusable ref \"$raw\" matches no entry")
            group.size > 1 -> parseError(path, "${from.ref}: confusable ref \"$raw\" matches ${group.size} entries")
            else -> group.single().ref
        }
        if (ref == from.ref) parseError(path, "${from.ref}: confusable ref to itself")
        return ref
    }

    private fun MutableList<String>.addUnlessPresent(ref: String) {
        if (ref !in this) add(ref)
    }

    /** Mirrors the realization `notes` rule: teaching aids are keyed by declared readers. */
    private fun languageMap(
        path: String,
        where: String,
        o: JsonObject,
        key: String,
        declared: Set<Language>,
    ): Map<Language, String> {
        val map = o.stringMap(path, where, key)
        for ((lang, value) in map) {
            if (lang !in declared) parseError(path, "$where: $key for undeclared language \"$lang\"")
            if (value.isBlank()) parseError(path, "$where: blank $key.$lang")
        }
        return map
    }

    private fun JsonObject.nonBlank(path: String, where: String, key: String): String? =
        optionalString(path, where, key)?.also {
            if (it.isBlank()) parseError(path, "$where: blank \"$key\"")
        }
}
