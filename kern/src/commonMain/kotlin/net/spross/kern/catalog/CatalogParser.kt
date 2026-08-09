package net.spross.kern.catalog

import kotlinx.serialization.json.JsonObject
import net.spross.kern.model.CardKind
import net.spross.kern.model.Language
import net.spross.kern.model.LanguageInfo
import net.spross.kern.trainer.PhraseTemplate
import net.spross.kern.trainer.TrainerKind

/** Wraps a [CatalogSource], folding every read into an FNV-1a 64 fingerprint. */
internal class FingerprintingSource(private val delegate: CatalogSource) {
    private var hash: ULong = 0xcbf29ce484222325uL

    fun read(path: String): String? {
        val text = delegate.read(path) ?: return null
        fold(path)
        fold(text)
        return text
    }

    fun require(path: String): String = read(path) ?: parseError(path, "missing required file")

    private fun fold(text: String) {
        for (byte in text.encodeToByteArray()) {
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x100000001b3uL
        }
        hash = hash xor 0xffuL // why: separates path/content segments so moves can't alias
        hash *= 0x100000001b3uL
    }

    fun fingerprint(): String = hash.toString(16).padStart(16, '0')
}

internal object CatalogParser {

    fun parseAreasManifest(path: String, text: String): List<AreaGroup> {
        val groups = parseJson(path, text).arr(path, "root").mapIndexed { i, el ->
            val o = el.obj(path, "groups[$i]")
            o.rejectUnknownKeys(path, "groups[$i]", setOf("group", "titles", "areas"))
            val id = o.requireString(path, "groups[$i]", "group")
            val entries = o["areas"]?.arr(path, "groups[$i].areas").orEmpty()
            if (entries.isEmpty()) parseError(path, "groups[$i] ($id): empty areas")
            val areas = mutableListOf<String>()
            val emojis = mutableMapOf<String, String>()
            entries.forEachIndexed { j, entry ->
                val context = "groups[$i].areas[$j]"
                val ao = entry.obj(path, context)
                ao.rejectUnknownKeys(path, context, setOf("area", "emoji"))
                val area = ao.requireString(path, context, "area")
                val emoji = ao.requireString(path, context, "emoji")
                if (!isWellFormedEmoji(emoji)) parseError(path, "$context ($area): bad emoji \"$emoji\"")
                areas += area
                emojis[area] = emoji
            }
            AreaGroup(id, o.stringMap(path, "groups[$i]", "titles"), areas, emojis)
        }
        val allAreas = groups.flatMap { it.areas }
        if (allAreas.size != allAreas.toSet().size) parseError(path, "duplicate area across groups")
        return groups
    }

    /** Same shape rule the catalog lint applies to concept emoji (`CatalogLintTest`). */
    private fun isWellFormedEmoji(s: String): Boolean =
        s.isNotBlank() && s.length <= 12 && s.all { it.code >= 0x2000 }

    fun parseLanguages(path: String, text: String): Map<Language, LanguageInfo> {
        val root = parseJson(path, text).obj(path, "root")
        return root.entries.associate { (code, el) ->
            val o = el.obj(path, code)
            o.rejectUnknownKeys(path, code, setOf("name", "englishName", "flag", "optionalVerbPrefixes", "articles"))
            val name = o.requireString(path, code, "name")
            if (name.isBlank()) parseError(path, "$code: blank name")
            val englishName = o.requireString(path, code, "englishName")
            if (englishName.isBlank()) parseError(path, "$code: blank englishName")
            val flag = o.requireString(path, code, "flag")
            if (!isEmojiFlagSequence(flag)) parseError(path, "$code: flag must be one emoji flag sequence")
            code to LanguageInfo(
                code = code,
                name = name,
                englishName = englishName,
                flag = flag,
                optionalVerbPrefixes = o.stringList(path, code, "optionalVerbPrefixes"),
                articles = o.stringList(path, code, "articles"),
            )
        }
    }

    /**
     * `catalog/languages/<lang>.json` → what THIS language calls the others, keyed by the
     * language being named. [nameable] bounds both those codes and the readers `notes`
     * addresses, so a typo'd code is a parse failure rather than a table entry nobody hits.
     * It is the declared languages PLUS every language the country atlas knows — the table
     * is the atlas drill's vocabulary as much as it is the phrases' ([CountryAtlas]).
     */
    fun parseLanguageNames(path: String, text: String, nameable: Set<Language>): Map<Language, LanguageName> {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("languageNames"))
        val entries = root["languageNames"]?.obj(path, "languageNames")
            ?: parseError(path, "missing \"languageNames\"")
        return entries.entries.associate { (code, el) ->
            if (code !in nameable) parseError(path, "name for undeclared language \"$code\"")
            val o = el.obj(path, code)
            o.rejectUnknownKeys(path, code, setOf("name", "in", "speak", "learn", "variants", "notes"))
            val variants = o.stringList(path, code, "variants")
            for (variant in variants) {
                if (variant.isBlank() || variant.trim() != variant) parseError(path, "$code: bad variant \"$variant\"")
            }
            val notes = o.stringMap(path, code, "notes")
            for ((reader, note) in notes) {
                if (reader !in nameable) parseError(path, "$code: note for undeclared language \"$reader\"")
                if (note.isBlank()) parseError(path, "$code: blank note.$reader")
            }
            code to LanguageName(
                name = o.trimmedString(path, code, "name"),
                inForm = o.trimmedString(path, code, "in"),
                speak = o.optionalTrimmedString(path, code, "speak"),
                learn = o.optionalTrimmedString(path, code, "learn"),
                variants = variants,
                notes = notes,
            )
        }
    }

    /** Exactly two regional-indicator code points (each a surrogate pair in UTF-16). */
    internal fun isEmojiFlagSequence(s: String): Boolean {
        if (s.length != 4) return false
        return (0..2 step 2).all { i ->
            s[i] == '\uD83C' && s[i + 1] in '\uDDE6'..'\uDDFF'
        }
    }

    fun parseConcepts(area: String, path: String, text: String, firstSeedIndex: Int): List<CatalogConcept> {
        val concepts = parseJson(path, text).arr(path, "root").mapIndexed { i, el ->
            val o = el.obj(path, "[$i]")
            o.rejectUnknownKeys(path, "[$i]", setOf("slug", "kind", "emoji", "components", "feminineOf"))
            val slug = o.requireString(path, "[$i]", "slug")
            if (slug.isEmpty() || '|' in slug || '/' in slug) parseError(path, "[$i]: bad slug \"$slug\"")
            val kind = when (val raw = o.requireString(path, slug, "kind")) {
                "noun" -> CardKind.Noun
                "verb" -> CardKind.Verb
                "adjective" -> CardKind.Adjective
                "phrase" -> CardKind.Phrase
                "idiom" -> CardKind.Idiom
                else -> parseError(path, "$slug: unknown kind \"$raw\"")
            }
            if (kind != CardKind.Phrase && "components" in o.keys) {
                parseError(path, "$slug: components on a ${kind.name.lowercase()}")
            }
            if (kind != CardKind.Noun && "feminineOf" in o.keys) parseError(path, "$slug: feminineOf on a non-noun")
            if (kind == CardKind.Idiom && "emoji" in o.keys) {
                parseError(path, "$slug: idioms use the fixed idiom emoji, not a per-concept one")
            }
            CatalogConcept(
                area = area,
                slug = slug,
                kind = kind,
                emoji = o.optionalString(path, slug, "emoji"),
                components = o.stringList(path, slug, "components"),
                feminineOf = o.optionalString(path, slug, "feminineOf"),
                seedIndex = firstSeedIndex + i,
            )
        }
        val slugs = concepts.map { it.slug }
        if (slugs.size != slugs.toSet().size) parseError(path, "duplicate slug within area")
        validateReferences(path, concepts)
        return concepts
    }

    private fun validateReferences(path: String, concepts: List<CatalogConcept>) {
        val bySlug = concepts.associateBy { it.slug }
        for (c in concepts) {
            for (component in c.components) {
                val target = bySlug[component] ?: parseError(path, "${c.slug}: unresolved component \"$component\"")
                if (target.kind == CardKind.Phrase) parseError(path, "${c.slug}: component \"$component\" is a phrase")
            }
            c.feminineOf?.let { base ->
                val target = bySlug[base] ?: parseError(path, "${c.slug}: unresolved feminineOf \"$base\"")
                if (target.kind != CardKind.Noun || target.slug == c.slug || target.feminineOf != null) {
                    parseError(path, "${c.slug}: feminineOf must reference a plain same-area noun")
                }
            }
        }
    }

    /** Returns the area's headings + realizations; validates slugs against its concepts. */
    fun parseAreaLanguageFile(
        path: String,
        text: String,
        conceptSlugs: Set<String>,
    ): RawArea {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("title", "subtitle", "words"))
        val title = root.requireString(path, "root", "title")
        if (title.isBlank()) parseError(path, "blank title")
        val subtitle = root.optionalString(path, "root", "subtitle")
        if (subtitle != null && subtitle.isBlank()) parseError(path, "blank subtitle")
        val wordsObj = root["words"]?.obj(path, "words") ?: parseError(path, "missing \"words\"")
        val words = wordsObj.entries.associate { (slug, el) ->
            if (slug !in conceptSlugs) parseError(path, "realization for unknown slug \"$slug\"")
            slug to parseRealization(path, slug, el.obj(path, slug))
        }
        return RawArea(title, subtitle, words)
    }

    /**
     * The frame manifest. Frame slugs live in the same namespace as concept slugs —
     * [conceptSlugs] keeps them disjoint, so nothing can address both a card and a drill.
     */
    fun parseFrames(path: String, text: String, conceptSlugs: Set<String>): List<CatalogFrame> {
        val frames = parseJson(path, text).arr(path, "root").mapIndexed { i, el ->
            val o = el.obj(path, "[$i]")
            o.rejectUnknownKeys(path, "[$i]", setOf("slug", "slot"))
            val slug = o.requireString(path, "[$i]", "slug")
            if (slug.isEmpty() || '|' in slug || '/' in slug) parseError(path, "[$i]: bad slug \"$slug\"")
            if (slug in conceptSlugs) parseError(path, "$slug: frame slug also names a concept")
            val slot = when (val raw = o.requireString(path, slug, "slot")) {
                "numbers" -> TrainerKind.Numbers
                "years" -> TrainerKind.Years
                "clock" -> TrainerKind.Clock
                "fraction" -> TrainerKind.Fraction
                else -> parseError(path, "$slug: unknown slot \"$raw\"")
            }
            CatalogFrame(slug, slot)
        }
        val slugs = frames.map { it.slug }
        if (slugs.size != slugs.toSet().size) parseError(path, "duplicate frame slug")
        return frames
    }

    /**
     * The language's number notes plus its frame realizations; validates slugs and markers
     * against [slots]. `numberNotes` is a ROOT key, so it never enters the slug namespace —
     * a frame may still be called that, and would be realized inside `frames` like any other.
     */
    fun parseFrameLanguageFile(
        path: String,
        text: String,
        slots: Map<String, TrainerKind>,
    ): RawDrills {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("numberNotes", "frames"))
        val notes = root.stringListMap(path, "root", "numberNotes")
        for ((reader, lines) in notes) {
            if (lines.isEmpty()) parseError(path, "numberNotes.$reader: no lines")
            for (line in lines) {
                if (line.isBlank() || line.trim() != line) parseError(path, "numberNotes.$reader: bad line \"$line\"")
            }
        }
        val framesObj = root["frames"]?.obj(path, "frames") ?: parseError(path, "missing \"frames\"")
        val frames = framesObj.entries.associate { (slug, el) ->
            val slot = slots[slug] ?: parseError(path, "frame for unknown slug \"$slug\"")
            slug to parseFrame(path, slug, slot, el.obj(path, slug))
        }
        return RawDrills(numberNotes = notes, frames = frames)
    }

    private fun parseFrame(path: String, slug: String, slot: TrainerKind, o: JsonObject): RawFrame {
        o.rejectUnknownKeys(path, slug, setOf("text", "variants", "count", "masculineNumeral", "notes"))
        val text = o.requireString(path, slug, "text")
        val variants = o.stringList(path, slug, "variants")
        val count = o["count"]?.let { el ->
            if (slot != TrainerKind.Numbers) parseError(path, "$slug: count on a ${slot.name.lowercase()} frame")
            val co = el.obj(path, "$slug.count")
            co.rejectUnknownKeys(path, "$slug.count", setOf("one", "few", "many"))
            PhraseTemplate.CountForms(
                one = co.requireString(path, "$slug.count", "one"),
                few = co.requireString(path, "$slug.count", "few"),
                many = co.requireString(path, "$slug.count", "many"),
            )
        }
        for (frame in listOf(text) + variants) {
            if (frame.isBlank()) parseError(path, "$slug: blank frame")
            LanguageNames.markerError(frame)?.let { parseError(path, "$slug: $it") }
            if (occurrences(frame, PhraseTemplate.SLOT_MARKER) != 1) {
                parseError(path, "$slug: \"$frame\" needs exactly one ${PhraseTemplate.SLOT_MARKER}")
            }
            if (occurrences(frame, PhraseTemplate.COUNT_MARKER) != (if (count == null) 0 else 1)) {
                parseError(path, "$slug: \"$frame\" carries ${PhraseTemplate.COUNT_MARKER} iff \"count\" is authored")
            }
        }
        return RawFrame(
            text = text,
            variants = variants,
            count = count,
            masculineNumeral = o.optionalBoolean(path, slug, "masculineNumeral") ?: false,
            notes = o.stringMap(path, slug, "notes"),
        )
    }

    private fun occurrences(text: String, marker: String): Int {
        var found = 0
        var index = text.indexOf(marker)
        while (index >= 0) {
            found++
            index = text.indexOf(marker, index + marker.length)
        }
        return found
    }

    private fun parseRealization(path: String, slug: String, o: JsonObject): RawRealization {
        o.rejectUnknownKeys(path, slug, setOf("text", "synonyms", "variants", "grammar", "notes"))
        val text = o.requireString(path, slug, "text")
        if (text.isBlank()) parseError(path, "$slug: blank text")
        val synonyms = o.stringList(path, slug, "synonyms")
        val variants = o.stringList(path, slug, "variants")
        for (form in listOf(text) + synonyms + variants) {
            LanguageNames.markerError(form)?.let { parseError(path, "$slug: $it") }
        }
        return RawRealization(
            text = text,
            synonyms = synonyms,
            variants = variants,
            grammar = o.stringMap(path, slug, "grammar"),
            notes = o.stringMap(path, slug, "notes"),
        )
    }
}
