package net.spross.kern.catalog

/**
 * Audio manifests over the [Fixture] catalog — one per declared language except `en`,
 * which stays recording-less so "no manifest at all" keeps its coverage. Synthetic
 * values, pinned by `CatalogAudioFixtureTest`; between them the entries carry every
 * reachability class of `kern/docs/audio.md` (exact, caseless, NFD/NFC, edge punctuation, stem
 * dash) plus both collision shapes.
 *
 * Provenance is FACTORED as the shipped manifests are: a license per author in `authors`,
 * its deed per license in `licenses`, and an entry naming a license only where it departs
 * from its author's. de Anna is that departure (BY-SA on two files, BY on `waiter`), which
 * also keeps one speaker's BY and BY-SA work in separate credit groups; uk `Public domain`
 * keeps the null-deed case.
 *
 * The analysis index appears in all three of its states: a boost with a lead skip (the uk
 * letters, which is what it exists for), an attenuation on a loud word (`sw` door), and
 * absent altogether, which has to read back as 0/0.
 *
 * de `waiter` carries BOTH a bare recording and an article one, which is the shape most of
 * the shipped German pack has: one word, two files, and which plays decided by whether the
 * card shows an article. de `mouse` is the other shape — an article recording and no bare
 * twin, which still answers a card asking for the bare word. de `waiter-f` stays recorded
 * neither way, so "nothing to play at all" keeps its coverage.
 */
internal object AudioFixture {
    private const val BY_SA = "https://creativecommons.org/licenses/by-sa/4.0/"
    private const val BY = "https://creativecommons.org/licenses/by/3.0/us/"

    val files: Map<String, String> = mapOf(
        // greet/royal are the de `morgen` homograph: two slugs, two recordings, one
        // speech key — ambiguous, so only their exact forms resolve.
        "audio/de/manifest.json" to """
            { "language": "de",
              "authors": { "Anna": "CC BY-SA 4.0", "Bert": "CC BY-SA 4.0", "Nina": "CC BY-SA 4.0" },
              "licenses": { "CC BY-SA 4.0": "$BY_SA", "CC BY 3.0 us": "$BY" },
              "words": {
                "cook":   { "file": "cook.mp3", "matches": "kochen",
                            "author": "Anna", "source": "De-kochen.ogg", "sha256": "d1" },
                "door":   { "file": "door.mp3", "matches": "Tür",
                            "author": "Anna", "source": "De-Tür.ogg", "sha256": "d2" },
                "greet":  { "file": "greet.mp3", "matches": "Morgen!",
                            "author": "Bert", "source": "De-Morgen.ogg", "sha256": "d3" },
                "hello":  { "file": "hello.mp3", "matches": "hallo", "license": "CC BY 3.0 us",
                            "author": "Bert", "source": "De-hallo.ogg", "sha256": "d4" },
                "royal":  { "file": "royal.mp3", "matches": "morgen",
                            "author": "Bert", "source": "De-morgen.ogg", "sha256": "d5" },
                "waiter": { "file": "waiter.mp3", "matches": "kellner", "license": "CC BY 3.0 us",
                            "author": "Anna", "source": "De-Kellner.ogg", "sha256": "d6" } },
              "articles": {
                "waiter": { "file": "articles/waiter.mp3", "matches": "der Kellner", "word": "Kellner",
                            "author": "Nina", "source": "LL-Q188 (deu)-Nina-der Kellner.wav",
                            "sha256": "d7", "gain": 4.2, "gainPhone": 2.1, "lead": 210, "snr": 61.0 },
                "mouse":  { "file": "articles/mouse.mp3", "matches": "die Maus", "word": "Maus",
                            "author": "Nina", "source": "LL-Q188 (deu)-Nina-die Maus.wav",
                            "sha256": "d8", "gain": 3.9, "gainPhone": 1.8, "snr": 60.2 } } }
        """.trimIndent(),
        // mouse/waiter are ONE recording fetched under two slugs (the sw slow/slower
        // shape): identical bytes, so the shared speech key still resolves.
        "audio/sw/manifest.json" to """
            { "language": "sw",
              "authors": { "Juma": "CC BY-SA 4.0" },
              "licenses": { "CC BY-SA 4.0": "$BY_SA" },
              "words": {
                "door":   { "file": "door.mp3", "matches": "mlango",
                            "author": "Juma", "source": "Sw-mlango.ogg", "sha256": "s1",
                            "gain": -5.4, "gainPhone": -9.8, "lead": 41, "snr": 62.3 },
                "mouse":  { "file": "mouse.mp3", "matches": "panya",
                            "author": "Juma", "source": "Sw-panya.ogg", "sha256": "s2" },
                "waiter": { "file": "waiter.mp3", "matches": "Panya",
                            "author": "Juma", "source": "Sw-panya.ogg", "sha256": "s2" } } }
        """.trimIndent(),
        "audio/uk/manifest.json" to """
            { "language": "uk",
              "authors": { "Ivan": "Public domain", "Halyna": "CC BY 3.0 us", "Tabrus": "CC BY-SA 4.0" },
              "licenses": { "Public domain": null, "CC BY 3.0 us": "$BY", "CC BY-SA 4.0": "$BY_SA" },
              "words": {
                "door":  { "file": "door.mp3", "matches": "двері",
                           "author": "Ivan", "source": "Uk-двері.ogg", "sha256": "u1" },
                "mouse": { "file": "mouse.mp3", "matches": "миша",
                           "author": "Halyna", "source": "Uk-миша.ogg", "sha256": "u2" } },
              "letters": {
                "ж": { "file": "letters/u0436.mp3",
                       "author": "Tabrus", "source": "Жж – ukrainian.ogg", "sha256": "u3",
                       "gain": 20.0, "lead": 1069 },
                "і": { "file": "letters/u0456.mp3",
                       "author": "Tabrus", "source": "Іі – ukrainian.ogg", "sha256": "u4",
                       "gain": 12.5, "lead": 604 } },
              "texts": {
                "джерело": { "file": "texts/u0434u0436u0435u0440u0435u043bu043e.mp3", "matches": "джерело",
                             "author": "Halyna", "source": "Uk-джерело.ogg", "sha256": "u5", "lead": 88 } } }
        """.trimIndent(),
    )

    /** The [Fixture] catalog with audio; `Fixture.catalog()` is the same content without it. */
    fun catalog(): Catalog = Catalog.load(MapCatalogSource(Fixture.files + files))

    /** The same catalog with one manifest replaced — for the parse-failure cases. */
    fun catalogWith(path: String, manifest: String): Catalog =
        Catalog.load(MapCatalogSource(Fixture.files + files + (path to manifest)))
}
