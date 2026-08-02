package net.spross.kern.trainer

import net.spross.kern.catalog.Alphabet
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetParser
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

/**
 * A synthetic alphabet the drill tests own outright — language `xx`, so nothing here can
 * be mistaken for shipped content and no authoring edit (the native-speaker sweep is
 * scheduled) can read as a code regression.
 *
 * Every schema shape the drill has to handle is in it: all four kinds, a name-prompted
 * letter run, a same-glyph pair (`ch` twice, ids required), both confusion axes, TWO
 * homophone pairs — `v`/`f` behind letter NAMES, where they are unanswerable, and `ß`/`ss`
 * inside a gap word, where they are the whole question — a `drill:false` grapheme that
 * stays a tile, an `exampleText` escape hatch, an `example` slug the language does not
 * realize (the provenance degradation case), a gap entry with no example at all (the
 * defensive pool filter), and a prose `rule` row that may never reach a tile.
 */
internal object LetterDrillFixture {
    const val LANGUAGE = "xx"

    private val json = """
        { "entries": [
          { "glyph": "m", "upper": "M", "name": "em", "ipa": "m",
            "hints": { "en": "as in moon" },
            "confusable": { "look": ["n"], "sound": ["n"] } },
          { "glyph": "n", "upper": "N", "name": "en", "ipa": "n",
            "hints": { "en": "as in noon" },
            "confusable": { "look": ["u", "h-length"], "sound": ["m"] } },
          { "glyph": "u", "upper": "U", "name": "u", "ipa": "uː",
            "hints": { "en": "as in boot" },
            "confusable": { "look": ["v"] } },
          { "glyph": "v", "upper": "V", "name": "vau", "ipa": "f",
            "hints": { "en": "an f in native words" },
            "confusable": { "sound": ["f"] } },
          { "glyph": "f", "upper": "F", "name": "ef", "ipa": "f",
            "hints": { "en": "as in fish" } },
          { "glyph": "ß", "kind": "digraph", "ipa": "s", "example": "street",
            "hints": { "en": "sharp s, never a b" },
            "confusable": { "look": ["ss"], "sound": ["ss"] } },
          { "glyph": "ss", "kind": "digraph", "ipa": "s", "exampleText": "Wasser",
            "hints": { "en": "after a short vowel" } },
          { "glyph": "ch", "kind": "contextual", "id": "ch-ich", "ipa": "ç",
            "example": "light", "exampleText": "Licht",
            "context": { "en": "after e, i" }, "hints": { "en": "the soft one" } },
          { "glyph": "ch", "kind": "contextual", "id": "ch-ach", "ipa": "x",
            "exampleText": "Nacht",
            "context": { "en": "after a, o, u" }, "hints": { "en": "the scraped one" },
            "confusable": { "look": ["ch-ich"], "sound": ["ch-ich"] } },
          { "glyph": "qu", "kind": "digraph", "ipa": "kv",
            "hints": { "en": "no example authored — unaskable, still a tile" } },
          { "glyph": "h", "kind": "contextual", "id": "h-length", "drill": false,
            "exampleText": "Kuh",
            "context": { "en": "after a vowel" }, "hints": { "en": "silent, it only lengthens" } },
          { "glyph": "b d g", "kind": "rule", "example": "street",
            "context": { "en": "word-finally" },
            "hints": { "en": "final devoicing: they end in p, t, k" } }
        ] }
    """.trimIndent()

    val alphabet: Alphabet =
        AlphabetParser.parse("alphabet/$LANGUAGE.json", json, LANGUAGE, setOf(LANGUAGE, "en"))

    /** What the app would hand in when the device can speak everything (§5.1). */
    val allRefs: List<String> = alphabet.entries.map { it.ref }

    /** The one slug `xx` realizes — `light` is deliberately absent (§2.2 degradation). */
    private val realized = mapOf("street" to "Straße")

    /**
     * The app's own resolver shape (§3.4): the concept realization wins and carries its
     * slug, `exampleText` steps in without one. An entry holding BOTH — `ch-ich` — falls
     * back to plain text here, which is what stamps it [LetterPromptKind.PlainText].
     *
     * One word per row on purpose: the pooled draw has to reproduce the single-example
     * behaviour exactly, which is what the golden sequences pin.
     */
    val example: (AlphabetEntry) -> List<LetterDrill.AlphabetExampleWord> = { entry ->
        listOfNotNull(
            entry.exampleSlug?.let { slug ->
                realized[slug]?.let { LetterDrill.AlphabetExampleWord(it, slug) }
            } ?: entry.exampleText?.let { LetterDrill.AlphabetExampleWord(it, null) },
        )
    }

    fun entry(ref: String): AlphabetEntry = requireNotNull(alphabet.entry(ref)) { "no entry \"$ref\"" }

    /** Consolidated box words, as §5.2 hands them over: single words of mixed length. */
    fun dictationCards(): List<Card> = listOf(
        card("ice", "Eis"),
        card("house", "Haus"),
        card("book", "Buch"),
        card("sun", "Sonne"),
        card("window", "Fenster"),
        card("rainbow", "Regenbogen"),
    )

    fun card(id: String, text: String, synonyms: List<String> = emptyList()): Card = Card(
        id = id,
        kind = CardKind.Noun,
        area = "fixture",
        emoji = null,
        seedIndex = 0,
        components = emptyList(),
        feminineOf = null,
        source = Realization(lang = "en", text = id),
        target = Realization(lang = LANGUAGE, text = text, synonyms = synonyms),
        promptFeminineMarker = false,
    )
}
