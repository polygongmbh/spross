package net.spross.kern.catalog

internal class MapCatalogSource(private val files: Map<String, String>) : CatalogSource {
    override fun read(path: String): String? = files[path]
}

/**
 * Inline fixture catalog exercising the join rules: feminine base-fallback + skip,
 * Sie/du variants, sparse coverage, "to " prefix, missing language files
 * (beta has no sw/en), seedIndex flattening across two groups, and
 * decomposed-Unicode forms (gamma/de door).
 */
internal object Fixture {
    private val du = "u\u0308" // decomposed u-umlaut: u + combining diaeresis

    val files: Map<String, String> = mapOf(
        "areas.json" to """
            [
             { "group": "start", "titles": { "de": "Start", "en": "Start", "sw": "Mwanzo", "uk": "Старт" },
               "areas": [{ "area": "alpha", "emoji": "🅰️" }] },
             { "group": "more", "titles": { "de": "Mehr", "en": "More", "sw": "Zaidi", "uk": "Більше" },
               "areas": [{ "area": "beta", "emoji": "🅱️" }, { "area": "gamma", "emoji": "🌀" }] }
            ]
        """.trimIndent(),
        "languages.json" to """
            {
             "de": { "name": "Deutsch", "englishName": "German", "flag": "🇩🇪",
                     "articles": ["der", "die", "das", "ein", "eine"] },
             "en": { "name": "English", "englishName": "English", "flag": "🇬🇧",
                     "optionalVerbPrefixes": ["to "], "articles": ["the", "a", "an"] },
             "sw": { "name": "Kiswahili", "englishName": "Swahili", "flag": "🇹🇿",
                     "optionalVerbPrefixes": ["ku", "kw"] },
             "uk": { "name": "Українська", "englishName": "Ukrainian", "flag": "🇺🇦" }
            }
        """.trimIndent(),
        "alpha/concepts.json" to """
            [
             { "slug": "waiter", "kind": "noun", "emoji": "🧑‍🍳" },
             { "slug": "waiter-f", "kind": "noun", "emoji": "👩‍🍳", "feminineOf": "waiter" },
             { "slug": "cook", "kind": "verb" },
             { "slug": "mouse", "kind": "noun", "emoji": "🐭" },
             { "slug": "hello", "kind": "phrase", "components": [] },
             { "slug": "the-mouse-runs", "kind": "phrase", "components": ["mouse", "cook"] },
             { "slug": "the-mouse-sprints", "kind": "phrase", "components": ["mouse", "cook"] }
            ]
        """.trimIndent(),
        "alpha/de.json" to """
            { "title": "Alpha",
              "words": {
                "waiter": { "text": "Kellner", "grammar": { "gender": "der", "plural": "=" } },
                "waiter-f": { "text": "Kellnerin", "grammar": { "gender": "die", "plural": "-nen" } },
                "cook": { "text": "kochen" },
                "mouse": { "text": "Maus", "grammar": { "gender": "die", "plural": "Mäuse" } },
                "hello": { "text": "Hallo!" },
                "the-mouse-runs": { "text": "Sehen Sie die Maus?", "variants": ["Siehst du die Maus?"] },
                "the-mouse-sprints": { "text": "Die Maus sprintet los." } } }
        """.trimIndent(),
        "alpha/en.json" to """
            { "title": "Alpha",
              "words": {
                "waiter": { "text": "waiter" },
                "cook": { "text": "to cook" },
                "mouse": { "text": "mouse" },
                "hello": { "text": "Hello!" },
                "the-mouse-runs": { "text": "Do you see the mouse?" },
                "the-mouse-sprints": { "text": "The mouse sprints off." } } }
        """.trimIndent(),
        "alpha/sw.json" to """
            { "title": "Alpha",
              "words": {
                "waiter": { "text": "mhudumu" },
                "cook": { "text": "kupika" },
                "mouse": { "text": "panya" },
                "hello": { "text": "Habari!" },
                "the-mouse-runs": { "text": "Unaona panya?" } } }
        """.trimIndent(),
        "alpha/uk.json" to """
            { "title": "Альфа",
              "words": {
                "waiter": { "text": "офіціант" },
                "waiter-f": { "text": "офіціантка" },
                "mouse": { "text": "миша", "synonyms": ["мишеня"], "variants": ["мишка"] },
                "the-mouse-sprints": { "text": "Миша спринтує.", "notes": { "de": "Nur im Fixture." } } } }
        """.trimIndent(),
        "beta/concepts.json" to """
            [
             { "slug": "royal", "kind": "noun" },
             { "slug": "royal-f", "kind": "noun", "feminineOf": "royal" },
             { "slug": "greet", "kind": "verb" }
            ]
        """.trimIndent(),
        "beta/de.json" to """
            { "title": "Beta",
              "words": {
                "royal": { "text": "Fürst", "grammar": { "gender": "der", "plural": "-en" } },
                "royal-f": { "text": "Fürstin", "grammar": { "gender": "die", "plural": "-nen" } },
                "greet": { "text": "grüßen" } } }
        """.trimIndent(),
        "beta/uk.json" to """
            { "title": "Бета",
              "words": {
                "royal-f": { "text": "княгиня" } } }
        """.trimIndent(),
        "gamma/concepts.json" to """
            [ { "slug": "door", "kind": "noun", "emoji": "🚪" } ]
        """.trimIndent(),
        "gamma/de.json" to """
            { "title": "Gamma",
              "words": {
                "door": { "text": "T${du}r", "synonyms": ["die  T${du}re"],
                          "grammar": { "gender": "die", "plural": "-en" } } } }
        """.trimIndent(),
        "gamma/sw.json" to """
            { "title": "Gamma", "words": { "door": { "text": "mlango" } } }
        """.trimIndent(),
        "gamma/uk.json" to """
            { "title": "Гамма", "words": { "door": { "text": "двері", "grammar": { "plural": "only" } } } }
        """.trimIndent(),
    )

    fun catalog(): Catalog = Catalog.load(MapCatalogSource(files))
}
