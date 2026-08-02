package net.spross.kern.catalog

/**
 * Alphabets over the [Fixture] catalog — de and uk only, so "no file at all" (en, sw)
 * keeps its coverage. Synthetic values, pinned by `AlphabetFixtureTest`; between them
 * the rows carry every schema shape: all four kinds, a three-way duplicate glyph with
 * ids, both confusion axes, a homophone pair, a `drill:false` grapheme, `exampleText`
 * escapes, and an `example` its own language does not realize (the degradation case).
 * de declares `sections` and uk declares none, so both halves of the optional grouping
 * are exercised by every test that loads the fixture.
 */
internal object AlphabetFixture {
    val files: Map<String, String> = mapOf(
        "alphabet/de.json" to """
            { "sections": [
              { "id": "letters", "title": { "de": "Buchstaben", "en": "Letters" } },
              { "id": "s-sounds", "title": { "en": "The s sounds" } },
              { "id": "ch", "title": { "en": "ch" } },
              { "id": "position", "title": { "en": "Where it stands" } }
            ],
            "entries": [
              { "glyph": "m", "upper": "M", "name": "em", "ipa": "m", "example": "mouse",
                "section": "letters",
                "hints": { "de": "wie in Maus", "en": "as in mouse" },
                "confusable": { "look": ["n"] } },
              { "glyph": "n", "upper": "N", "name": "en", "ipa": "n", "section": "letters",
                "hints": { "en": "as in no" } },
              { "glyph": "ü", "upper": "Ü", "name": "ü", "ipa": "yː", "example": "door",
                "section": "letters",
                "hints": { "en": "round your lips and say ee" } },
              { "glyph": "ß", "kind": "digraph", "ipa": "s", "example": "greet",
                "section": "s-sounds",
                "hints": { "en": "sharp s, never a b" },
                "confusable": { "look": ["ss"], "sound": ["ss"] } },
              { "glyph": "ss", "kind": "digraph", "ipa": "s", "exampleText": "Wasser",
                "section": "s-sounds",
                "hints": { "en": "after a short vowel" } },
              { "glyph": "ch", "kind": "contextual", "id": "ch-ich", "ipa": "ç",
                "context": { "en": "after e, i" }, "example": "cook", "section": "ch",
                "hints": { "en": "the soft one" },
                "confusable": { "look": ["ch-ach", "ch-chef"], "sound": ["ch-ach"] } },
              { "glyph": "ch", "kind": "contextual", "id": "ch-ach", "ipa": "x",
                "context": { "en": "after a, o, u" }, "exampleText": "Nacht", "section": "ch",
                "hints": { "en": "the scraped one" } },
              { "glyph": "ch", "kind": "contextual", "id": "ch-chef", "ipa": "ʃ",
                "context": { "en": "in French loans" }, "exampleText": "Chef", "section": "ch",
                "hints": { "en": "sh, as French left it" } },
              { "glyph": "h", "kind": "contextual", "id": "h-length", "drill": false,
                "context": { "en": "after a vowel" }, "exampleText": "Kuh", "section": "position",
                "hints": { "en": "silent — it only lengthens" } },
              { "glyph": "b d g", "kind": "rule", "drill": true, "example": "royal",
                "section": "position",
                "context": { "en": "word-finally" },
                "hints": { "en": "final devoicing: Fürst ends in a t sound" } }
            ] }
        """.trimIndent(),
        "alphabet/uk.json" to """
            { "entries": [
              { "glyph": "и", "upper": "И", "name": "и", "ipa": "ɪ", "example": "mouse",
                "hints": { "en": "lax i, as in bit" },
                "confusable": { "look": ["і"], "sound": ["і"] } },
              { "glyph": "і", "upper": "І", "name": "і", "ipa": "i",
                "hints": { "en": "tense ee, as in see" } },
              { "glyph": "ʼ", "name": "апостроф", "drill": false,
                "hints": { "en": "no sound of its own" } },
              { "glyph": "дж", "kind": "digraph", "ipa": "dʒ", "example": "greet",
                "exampleText": "джерело", "hints": { "en": "one sound, like j in jam" } }
            ] }
        """.trimIndent(),
    )

    /** The [Fixture] catalog with alphabets; audio stays out — the two are independent. */
    fun catalog(): Catalog = Catalog.load(MapCatalogSource(Fixture.files + files))

    /** The same catalog with one alphabet replaced — for the parse-failure cases. */
    fun catalogWith(path: String, alphabet: String): Catalog =
        Catalog.load(MapCatalogSource(Fixture.files + files + (path to alphabet)))

    /** A de alphabet built from [entries] alone, for one-rule failure cases. */
    fun deEntries(entries: String): Catalog =
        catalogWith("alphabet/de.json", """{ "entries": [$entries] }""")
}
