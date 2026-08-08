package net.spross.kern.catalog

internal class MapCatalogSource(private val files: Map<String, String>) : CatalogSource {
    override fun read(path: String): String? = files[path]
}

/**
 * Inline fixture catalog exercising the join rules: feminine base-fallback + skip,
 * Sie/du variants, sparse coverage, "to " prefix, missing language files
 * (beta has no sw/en), seedIndex flattening across two groups,
 * decomposed-Unicode forms (gamma/de door), area subtitles (gamma authors them,
 * alpha and beta none), the [drills] frames, the [names] tables and the language markers
 * gamma's phrases carry.
 */
internal object Fixture {
    private val du = "u\u0308" // decomposed u-umlaut: u + combining diaeresis

    /**
     * Frame files, kept apart so a test can drop them wholesale and exercise the
     * absent-`drills/` case. `learning-since-year` is German-only (never joins), and `fr`
     * is realized although no trainer pack covers it (the availability gate).
     * `numberNotes` covers all three reader cases: sw authors both readers, uk English
     * only (the fallback), de and fr none at all.
     */
    val drills: Map<String, String> = mapOf(
        "drills/frames.json" to """
            [ { "slug": "bus-arrives-at", "slot": "clock" },
              { "slug": "i-have-n-keys", "slot": "numbers" },
              { "slug": "learning-since-year", "slot": "years" } ]
        """.trimIndent(),
        "drills/de.json" to """
            { "frames": {
                "bus-arrives-at": { "text": "Der Bus kommt um {slot} Uhr." },
                "i-have-n-keys": { "text": "Ich habe {slot} Schl\u00fcssel.",
                                   "variants": ["Ich habe {slot} Schluessel."] },
                "learning-since-year": { "text": "Ich lerne seit {slot}." } } }
        """.trimIndent(),
        "drills/fr.json" to """
            { "frames": { "bus-arrives-at": { "text": "Le bus arrive à {slot}." } } }
        """.trimIndent(),
        "drills/sw.json" to """
            { "numberNotes": {
                "de": ["Sechs, sieben und neun sind entlehnt."],
                "en": ["Six, seven and nine are loans."] },
              "frames": {
                "bus-arrives-at": { "text": "Basi linakuja {slot}." },
                "i-have-n-keys": { "text": "Nina funguo {slot}." } } }
        """.trimIndent(),
        "drills/uk.json" to """
            { "numberNotes": { "en": ["The numeral sets the form."] },
              "frames": {
                "bus-arrives-at": { "text": "\u0410\u0432\u0442\u043e\u0431\u0443\u0441 {slot}." },
                "i-have-n-keys": { "text": "\u0423 \u043c\u0435\u043d\u0435 \u0454 {slot} {count}.",
                                   "count": { "one": "\u043a\u043b\u044e\u0447", "few": "\u043a\u043b\u044e\u0447\u0456", "many": "\u043a\u043b\u044e\u0447\u0456\u0432" },
                                   "masculineNumeral": true,
                                   "notes": { "de": "Zahlwort-Kongruenz." } } } }
        """.trimIndent(),
    )

    /**
     * Inflected language names, kept apart like [drills] so a test can drop them. Coverage
     * is deliberately partial: en and fr author no table at all, sw names no fr, and uk's
     * sw entry omits `speak` (the fallback to `name`).
     */
    val names: Map<String, String> = mapOf(
        "languages/de.json" to """
            { "languageNames": {
                "de": { "name": "Deutsch", "in": "auf Deutsch" },
                "en": { "name": "Englisch", "in": "auf Englisch" },
                "fr": { "name": "Französisch", "in": "auf Französisch" },
                "sw": { "name": "Suaheli", "in": "auf Suaheli", "variants": ["Kisuaheli"] },
                "uk": { "name": "Ukrainisch", "in": "auf Ukrainisch" } } }
        """.trimIndent(),
        "languages/sw.json" to """
            { "languageNames": {
                "de": { "name": "Kijerumani", "in": "kwa Kijerumani" },
                "en": { "name": "Kiingereza", "in": "kwa Kiingereza" },
                "sw": { "name": "Kiswahili", "in": "kwa Kiswahili" },
                "uk": { "name": "Kiukreni", "in": "kwa Kiukreni" } } }
        """.trimIndent(),
        "languages/uk.json" to """
            { "languageNames": {
                "de": { "name": "німецька", "in": "німецькою",
                        "speak": "німецькою", "learn": "німецьку" },
                "sw": { "name": "суахілі", "in": "мовою суахілі", "learn": "суахілі" },
                "uk": { "name": "українська", "in": "українською",
                        "speak": "українською", "learn": "українську" } } }
        """.trimIndent(),
    )

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
             "fr": { "name": "Français", "englishName": "French", "flag": "🇫🇷" },
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
            [ { "slug": "door", "kind": "noun", "emoji": "🚪" },
              { "slug": "im-learning", "kind": "phrase", "components": [] },
              { "slug": "i-speak-a-little", "kind": "phrase", "components": [] },
              { "slug": "how-do-you-say-this", "kind": "phrase", "components": [] } ]
        """.trimIndent(),
        "gamma/de.json" to """
            { "title": "Gamma", "subtitle": "Alles dreht sich.",
              "words": {
                "door": { "text": "T${du}r", "synonyms": ["die  T${du}re"],
                          "grammar": { "gender": "die", "plural": "-en" } },
                "im-learning": { "text": "Ich lerne {language}." },
                "i-speak-a-little": { "text": "Ich spreche ein bisschen {language}." },
                "how-do-you-say-this": { "text": "Wie sagt man das {language-in}?" } } }
        """.trimIndent(),
        // why: en authors realizations but NO languages/en.json — every pair with en as a
        // side must drop the marked concepts, which is the coverage-drop case.
        "gamma/en.json" to """
            { "title": "Gamma",
              "words": { "im-learning": { "text": "I'm learning {language}." } } }
        """.trimIndent(),
        "gamma/sw.json" to """
            { "title": "Gamma", "subtitle": "Kila kitu kinazunguka.",
              "words": {
                "door": { "text": "mlango" },
                "im-learning": { "text": "Ninajifunza {language}." },
                "i-speak-a-little": { "text": "Ninazungumza {language} kidogo." },
                "how-do-you-say-this": { "text": "Hii inasemwaje {language-in}?" } } }
        """.trimIndent(),
        "gamma/uk.json" to """
            { "title": "Гамма", "subtitle": "Усе обертається.",
              "words": {
                "door": { "text": "двері", "grammar": { "plural": "only" } },
                "im-learning": { "text": "Я вчу {language-learn}." },
                "i-speak-a-little": { "text": "Я трохи розмовляю {language-speak}." },
                "how-do-you-say-this": { "text": "Як це сказати {language-in}?" } } }
        """.trimIndent(),
    ) + drills + names

    fun catalog(): Catalog = Catalog.load(MapCatalogSource(files))
}
