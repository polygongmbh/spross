package net.spross.kern.catalog

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import net.spross.kern.model.CardKind
import net.spross.kern.model.FormTag
import net.spross.kern.model.Language
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.TaggedForm

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
            o.rejectUnknownKeys(
                path,
                code,
                setOf("name", "englishName", "flag", "optionalVerbPrefixes", "articles", "diacriticDigraphs"),
            )
            val name = o.requireString(path, code, "name")
            if (name.isBlank()) parseError(path, "$code: blank name")
            val englishName = o.requireString(path, code, "englishName")
            if (englishName.isBlank()) parseError(path, "$code: blank englishName")
            val flag = o.requireString(path, code, "flag")
            // why: Esperanto has no country, so no RIS flag exists — its badge is the
            // community's green heart; every other language keeps the strict flag rule.
            val esperantoBadge = code == "eo" && flag == "💚"
            if (!isEmojiFlagSequence(flag) && !esperantoBadge) {
                parseError(path, "$code: flag must be one emoji flag sequence")
            }
            code to LanguageInfo(
                code = code,
                name = name,
                englishName = englishName,
                flag = flag,
                optionalVerbPrefixes = o.stringList(path, code, "optionalVerbPrefixes"),
                articles = o.stringList(path, code, "articles"),
                diacriticDigraphs = o.stringMap(path, code, "diacriticDigraphs"),
            )
        }
    }

    /**
     * `catalog/language-names/<lang>.json` → what THIS language calls the others, keyed by the
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
            o.rejectUnknownKeys(path, code, setOf("name", "in", "speak", "learn", "accepts", "notes"))
            val accepts = o.stringList(path, code, "accepts")
            for (form in accepts) {
                if (form.isBlank() || form.trim() != form) parseError(path, "$code: bad accepts entry \"$form\"")
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
                accepts = accepts,
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

    private fun parseRealization(path: String, slug: String, o: JsonObject): RawRealization {
        o.rejectUnknownKeys(path, slug, setOf("text", "teaches", "accepts", "forms", "orders", "grammar", "notes"))
        val text = o.requireString(path, slug, "text")
        if (text.isBlank()) parseError(path, "$slug: blank text")
        val teaches = o.stringList(path, slug, "teaches")
        val accepts = o.stringList(path, slug, "accepts")
        val orders = o.stringList(path, slug, "orders")
        val forms = parseForms(path, slug, o)
        for (form in listOf(text) + teaches + accepts + forms.map { it.text } + orders) {
            LanguageNames.markerError(form)?.let { parseError(path, "$slug: $it") }
        }
        return RawRealization(
            text = text,
            teaches = teaches,
            accepts = accepts,
            forms = forms,
            orders = orders,
            grammar = o.stringMap(path, slug, "grammar"),
            notes = o.stringMap(path, slug, "notes"),
        )
    }

    /** `"forms": { "f": "larga", "f.pl": ["largas"] }` — a value is one form or several. */
    private fun parseForms(path: String, slug: String, o: JsonObject): List<TaggedForm> {
        val obj = o["forms"]?.obj(path, "$slug.forms") ?: return emptyList()
        return obj.entries.flatMap { (key, value) ->
            val tag = FormTag.parse(key) ?: parseError(path, "$slug: unknown form tag \"$key\"")
            val texts = (value as? JsonArray)?.mapIndexed { i, el -> el.str(path, "$slug.forms.$key[$i]") }
                ?: listOf(value.str(path, "$slug.forms.$key"))
            texts.map { TaggedForm(tag, it) }
        }
    }
}
