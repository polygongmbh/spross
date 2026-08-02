package net.spross.kern.catalog

/**
 * Audio manifests over the [Fixture] catalog — one per declared language except `en`,
 * which stays recording-less so "no manifest at all" keeps its coverage. Synthetic
 * values, pinned by `CatalogAudioFixtureTest`; between them the entries carry every
 * reachability class of README §11 (exact, caseless, NFD/NFC, edge punctuation, stem
 * dash) plus both collision shapes.
 *
 * The analysis index appears in all three of its states: a boost with a lead skip (the uk
 * letters, which is what it exists for), an attenuation on a loud word (`sw` door), and
 * absent altogether, which has to read back as 0/0.
 */
internal object AudioFixture {
    private const val BY_SA = "https://creativecommons.org/licenses/by-sa/4.0/"
    private const val BY = "https://creativecommons.org/licenses/by/3.0/us/"

    val files: Map<String, String> = mapOf(
        // greet/royal are the de `morgen` homograph: two slugs, two recordings, one
        // speech key — ambiguous, so only their exact forms resolve.
        "audio/de/manifest.json" to """
            { "language": "de",
              "words": {
                "cook":   { "file": "cook.mp3", "matches": "kochen", "licence": "CC BY-SA 4.0",
                            "licenceUrl": "$BY_SA", "author": "Anna", "source": "De-kochen.ogg", "sha256": "d1" },
                "door":   { "file": "door.mp3", "matches": "Tür", "licence": "CC BY-SA 4.0",
                            "licenceUrl": "$BY_SA", "author": "Anna", "source": "De-Tür.ogg", "sha256": "d2" },
                "greet":  { "file": "greet.mp3", "matches": "Morgen!", "licence": "CC BY-SA 4.0",
                            "licenceUrl": "$BY_SA", "author": "Bert", "source": "De-Morgen.ogg", "sha256": "d3" },
                "hello":  { "file": "hello.mp3", "matches": "hallo", "licence": "CC BY 3.0 us",
                            "licenceUrl": "$BY", "author": "Bert", "source": "De-hallo.ogg", "sha256": "d4" },
                "royal":  { "file": "royal.mp3", "matches": "morgen", "licence": "CC BY-SA 4.0",
                            "licenceUrl": "$BY_SA", "author": "Bert", "source": "De-morgen.ogg", "sha256": "d5" },
                "waiter": { "file": "waiter.mp3", "matches": "kellner", "licence": "CC BY 3.0 us",
                            "licenceUrl": "$BY", "author": "Anna", "source": "De-Kellner.ogg", "sha256": "d6" } } }
        """.trimIndent(),
        // mouse/waiter are ONE recording fetched under two slugs (the sw slow/slower
        // shape): identical bytes, so the shared speech key still resolves.
        "audio/sw/manifest.json" to """
            { "language": "sw",
              "words": {
                "door":   { "file": "door.mp3", "matches": "mlango", "licence": "CC BY-SA 4.0",
                            "licenceUrl": "$BY_SA", "author": "Juma", "source": "Sw-mlango.ogg", "sha256": "s1",
                            "gain": -5.4, "lead": 41 },
                "mouse":  { "file": "mouse.mp3", "matches": "panya", "licence": "CC BY-SA 4.0",
                            "licenceUrl": "$BY_SA", "author": "Juma", "source": "Sw-panya.ogg", "sha256": "s2" },
                "waiter": { "file": "waiter.mp3", "matches": "Panya", "licence": "CC BY-SA 4.0",
                            "licenceUrl": "$BY_SA", "author": "Juma", "source": "Sw-panya.ogg", "sha256": "s2" } } }
        """.trimIndent(),
        "audio/uk/manifest.json" to """
            { "language": "uk",
              "words": {
                "door":  { "file": "door.mp3", "matches": "двері", "licence": "Public domain",
                           "author": "Ivan", "source": "Uk-двері.ogg", "sha256": "u1" },
                "mouse": { "file": "mouse.mp3", "matches": "миша", "licence": "CC BY 3.0 us",
                           "licenceUrl": "$BY", "author": "Halyna", "source": "Uk-миша.ogg", "sha256": "u2" } },
              "letters": {
                "ж": { "file": "letters/u0436.mp3", "licence": "CC BY-SA 4.0", "licenceUrl": "$BY_SA",
                       "author": "Tabrus", "source": "Жж – ukrainian.ogg", "sha256": "u3",
                       "gain": 20.0, "lead": 1069 },
                "і": { "file": "letters/u0456.mp3", "licence": "CC BY-SA 4.0", "licenceUrl": "$BY_SA",
                       "author": "Tabrus", "source": "Іі – ukrainian.ogg", "sha256": "u4",
                       "gain": 12.5, "lead": 604 } },
              "texts": {
                "джерело": { "file": "texts/u0434u0436u0435u0440u0435u043bu043e.mp3", "matches": "джерело",
                             "licence": "CC BY 3.0 us", "licenceUrl": "$BY", "author": "Halyna",
                             "source": "Uk-джерело.ogg", "sha256": "u5", "lead": 88 } } }
        """.trimIndent(),
    )

    /** The [Fixture] catalog with audio; `Fixture.catalog()` is the same content without it. */
    fun catalog(): Catalog = Catalog.load(MapCatalogSource(Fixture.files + files))

    /** The same catalog with one manifest replaced — for the parse-failure cases. */
    fun catalogWith(path: String, manifest: String): Catalog =
        Catalog.load(MapCatalogSource(Fixture.files + files + (path to manifest)))
}
