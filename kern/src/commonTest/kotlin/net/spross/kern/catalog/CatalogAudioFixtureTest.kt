package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The audio half of [CatalogFixtureTest] — parse, lookup, utterance and credits over
 * the synthetic [AudioFixture], which owns its values so they can be pinned exactly.
 * Real-catalog rules live in `CatalogAudioLintTest`.
 */
class CatalogAudioFixtureTest {
    private val catalog = AudioFixture.catalog()

    private fun recording(lang: String, form: String): String? =
        catalog.pronunciation(lang, form).recordingPath

    // -- parse -------------------------------------------------------------------------

    /**
     * A `texts{}` entry voices an alphabet `exampleText` — reference material citing no
     * concept. It shares the WORDS' form index rather than getting a lookup of its own,
     * so the sheet, the drill and a review card all reach it through one code path, and
     * none of them can play it over a word it does not say.
     */
    @Test
    fun anExampleTextIsFoundByTheFormItSpeaks() {
        assertEquals("audio/uk/texts/u0434u0436u0435u0440u0435u043bu043e.mp3", recording("uk", "джерело"))
        // Same folding as words: edge punctuation is spelling, not speech.
        assertEquals("audio/uk/texts/u0434u0436u0435u0440u0435u043bu043e.mp3", recording("uk", "Джерело!"))
        assertNull(recording("uk", "джерела"))
        val text = catalog.audio.getValue("uk").texts.getValue("джерело")
        assertEquals("джерело", text.matches)
        assertEquals(88L, text.leadMs)
    }

    @Test
    fun everyAuthoredFieldLandsWhereItWasAuthored() {
        val word = catalog.audio.getValue("uk").words.getValue("mouse")
        assertEquals("mouse.mp3", word.file)
        assertEquals("миша", word.matches)
        assertEquals("CC BY 3.0 us", word.license)
        assertEquals("https://creativecommons.org/licenses/by/3.0/us/", word.licenseUrl)
        assertEquals("Halyna", word.author)
        assertEquals("Uk-миша.ogg", word.source)
        assertEquals("u2", word.sha256)
        // A letter carries the same shape minus the spoken form.
        val letter = catalog.audio.getValue("uk").letters.getValue("ж")
        assertEquals("letters/u0436.mp3", letter.file)
        assertNull(letter.matches)
        assertEquals("Tabrus", letter.author)
        // Public-domain files have no deed to link.
        assertNull(catalog.audio.getValue("uk").words.getValue("door").licenseUrl)
    }

    // -- the analysis index ------------------------------------------------------------

    @Test
    fun theIndexIsReadWhereItIsAuthoredAndZeroWhereItIsNot() {
        val letter = catalog.audio.getValue("uk").letters.getValue("ж")
        assertEquals(20.0, letter.gain) // quiet: boosted toward the target
        assertEquals(1069L, letter.leadMs)
        val loud = catalog.audio.getValue("sw").words.getValue("door")
        assertEquals(-5.4, loud.gain) // loud: the same field, the other sign
        assertEquals(-9.8, loud.gainPhone) // the phone plane, measured through the lens
        assertEquals(41L, loud.leadMs)
        // Absent is not "unknown" — it is a recording with nothing to correct.
        val plain = catalog.audio.getValue("uk").words.getValue("mouse")
        assertEquals(0.0, plain.gain)
        assertNull(plain.gainPhone) // no phone plane measured on this entry
        assertEquals(0L, plain.leadMs)
        // `snr` rides along the same way, but corrects nothing — it is carried so lint can
        // see how clean a pack is, and never reaches a player.
        assertEquals(62.3, loud.snr)
        assertEquals(0.0, plain.snr)
    }

    @Test
    fun aWordCarriesItsIndexToThePronunciation() {
        val spoken = catalog.pronunciation("sw", "-mlango")
        assertEquals(-5.4, spoken.gain)
        assertEquals(-9.8, spoken.gainPhone)
        assertEquals(41L, spoken.leadMs)
        // Nothing to play means nothing to correct, not a stale index from elsewhere.
        val synthesized = catalog.pronunciation("de", "Kellnerin")
        assertNull(synthesized.recordingPath)
        assertEquals(0.0, synthesized.gain)
        assertNull(synthesized.gainPhone)
        assertEquals(0L, synthesized.leadMs)
    }

    @Test
    fun aLetterCarriesItsIndexBesideItsPath() {
        val letter = assertNotNull(catalog.letterRecording("uk", "ж"))
        assertEquals("audio/uk/letters/u0436.mp3", letter.path)
        assertEquals(20.0, letter.gain)
        assertNull(letter.gainPhone) // letters ship no phone plane
        assertEquals(1069L, letter.leadMs)
        assertNull(catalog.letterRecording("uk", "ь")) // no recording exists
        assertNull(catalog.letterRecording("en", "ж")) // no manifest at all
    }

    @Test
    fun anIndexOutsideMeasurementRangeIsAParseError() {
        for ((field, value) in listOf("gain" to "20.1", "gain" to "-40", "lead" to "5001", "lead" to "-1")) {
            val error = assertFailsWith<CatalogFormatException>("$field=$value was accepted") {
                AudioFixture.catalogWith("audio/uk/manifest.json", letterManifest("\"$field\": $value"))
            }
            assertTrue(field in error.message.orEmpty(), error.message.orEmpty())
        }
    }

    @Test
    fun anIndexThatIsNotANumberIsAParseError() {
        // A quoted measurement is a generator bug, and a fractional millisecond is another.
        for (authored in listOf("\"gain\": \"20.0\"", "\"lead\": 1069.5")) {
            assertFailsWith<CatalogFormatException>("$authored was accepted") {
                AudioFixture.catalogWith("audio/uk/manifest.json", letterManifest(authored))
            }
        }
    }

    private fun letterManifest(index: String): String =
        """
        { "language": "uk",
          "letters": { "ж": { "file": "letters/u0436.mp3", "license": "Public domain",
                              "author": "Tabrus", "source": "s.ogg", "sha256": "u3", $index } } }
        """.trimIndent()

    @Test
    fun unknownKeysAreRejectedWithTheirPath() {
        val error = assertFailsWith<CatalogFormatException> {
            AudioFixture.catalogWith(
                "audio/uk/manifest.json",
                """
                { "language": "uk",
                  "words": { "mouse": { "file": "mouse.mp3", "matches": "миша", "license": "Public domain",
                                        "author": "Halyna", "source": "s.ogg", "sha256": "u2", "voice": "alto" } } }
                """.trimIndent(),
            )
        }
        val message = error.message.orEmpty()
        assertTrue("audio/uk/manifest.json" in message, message)
        assertTrue("voice" in message, message)
    }

    @Test
    fun aManifestMustDeclareTheLanguageItSitsUnder() {
        val error = assertFailsWith<CatalogFormatException> {
            AudioFixture.catalogWith("audio/uk/manifest.json", """{ "language": "de" }""")
        }
        assertTrue("expected \"uk\"" in error.message.orEmpty(), error.message.orEmpty())
    }

    @Test
    fun aLetterMayNotClaimASpokenForm() {
        // The recording speaks the letter's NAME — a `matches` there would be a lie the
        // word index would then act on.
        val error = assertFailsWith<CatalogFormatException> {
            AudioFixture.catalogWith(
                "audio/uk/manifest.json",
                """
                { "language": "uk",
                  "letters": { "ж": { "file": "letters/u0436.mp3", "matches": "же", "license": "Public domain",
                                      "author": "Tabrus", "source": "s.ogg", "sha256": "u3" } } }
                """.trimIndent(),
            )
        }
        assertTrue("matches" in error.message.orEmpty(), error.message.orEmpty())
    }

    // -- speechKey / utterance ---------------------------------------------------------

    @Test
    fun speechKeyFoldsAwayEverythingThatIsSpellingRatherThanSpeech() {
        assertEquals("unterlagen", speechKey("Unterlagen"))
        assertEquals("hujambo", speechKey("Hujambo!"))
        assertEquals("hola", speechKey("¡Hola!")) // Spanish opens what it closes
        assertEquals(speechKey("zuri"), speechKey("-zuri"))
        assertEquals("tür", speechKey("Tu\u0308r")) // decomposed input, composed key
        // an INNER apostrophe is part of the word — one class, three spellings, one key:
        // Commons titles French elision with U+2019, the catalog writes U+0027.
        assertEquals("ім\u02bcя", speechKey("ім'я"))
        assertEquals(speechKey("s'habiller"), speechKey("s\u2019habiller"))
    }

    @Test
    fun utteranceDropsTheCitationDashAndKeepsProsody() {
        assertEquals("zuri", utterance("-zuri"))
        assertEquals("Hujambo!", utterance("Hujambo!"))
        assertEquals("Kellner", utterance("Kellner"))
    }

    // -- lookup ------------------------------------------------------------------------

    @Test
    fun aRecordingOfTheVisibleFormPlays() {
        assertEquals("audio/de/cook.mp3", recording("de", "kochen")) // exact
        assertEquals("audio/de/waiter.mp3", recording("de", "Kellner")) // recorded "kellner"
        assertEquals("audio/de/door.mp3", recording("de", "Tu\u0308r")) // decomposed on screen
        assertEquals("audio/de/hello.mp3", recording("de", "Hallo!")) // recorded "hallo"
        assertEquals("audio/sw/door.mp3", recording("sw", "-mlango")) // sw stem citation
    }

    @Test
    fun aFormNoRecordingSpeaksFallsThrough() {
        // The rotated synonym of uk mouse was never recorded — TTS speaks it instead.
        assertNull(recording("uk", "мишеня"))
        assertNull(recording("de", "Kellnerin"))
    }

    @Test
    fun anAmbiguousSpeechKeyPlaysNothing() {
        // de greet/royal: two recordings, one speech key, different bytes — no right guess.
        assertNull(recording("de", "Morgen"))
        // Their exact forms still resolve: an exact hit is never a guess.
        assertEquals("audio/de/greet.mp3", recording("de", "Morgen!"))
        assertEquals("audio/de/royal.mp3", recording("de", "morgen"))
    }

    @Test
    fun oneRecordingUnderTwoSlugsStillResolves() {
        // sw mouse/waiter share bytes, so the shared key is not ambiguous at all.
        assertEquals("audio/sw/mouse.mp3", recording("sw", "Panya!"))
    }

    @Test
    fun lettersAreFoundByGlyphAndNowhereElse() {
        assertEquals("audio/uk/letters/u0436.mp3", catalog.letterRecordingPath("uk", "ж"))
        assertNull(catalog.letterRecordingPath("uk", "ь")) // no recording exists
        assertNull(catalog.letterRecordingPath("de", "ж")) // de ships no letters section
        assertNull(recording("uk", "ж")) // letters never enter the word index
    }

    @Test
    fun aLanguageWithoutAManifestIsSimplySilent() {
        assertNull(recording("en", "waiter"))
        assertNull(catalog.letterRecordingPath("en", "ж"))
    }

    @Test
    fun pronunciationCarriesTheFormItWasAskedAbout() {
        val spoken = catalog.pronunciation("sw", "-mlango")
        assertEquals("-mlango", spoken.form)
        assertEquals("mlango", spoken.utterance)
        assertEquals("sw", spoken.lang)
        assertEquals("audio/sw/door.mp3", spoken.recordingPath)
    }

    // -- credits -----------------------------------------------------------------------

    @Test
    fun creditsGroupPerLanguageAuthorAndLicense() {
        val credits = catalog.audioCredits()
        assertEquals(
            listOf(
                "de|Anna|CC BY-SA 4.0", // one author's BY-SA and BY work stays apart
                "de|Bert|CC BY-SA 4.0",
                "de|Bert|CC BY 3.0 us",
                "de|Anna|CC BY 3.0 us",
                "sw|Juma|CC BY-SA 4.0",
                "uk|Ivan|Public domain",
                "uk|Halyna|CC BY 3.0 us",
                "uk|Tabrus|CC BY-SA 4.0",
            ),
            credits.map { "${it.language}|${it.author}|${it.license}" },
        )
        assertEquals(
            listOf(AudioCreditFile("kochen", "De-kochen.ogg"), AudioCreditFile("Tür", "De-Tür.ogg")),
            credits.first().files,
        )
        assertNull(credits.first { it.license == "Public domain" }.licenseUrl)
    }

    @Test
    fun lettersAreCreditedByTheirGlyph() {
        val letters = catalog.audioCredits().first { it.author == "Tabrus" }
        assertEquals(listOf("ж", "і"), letters.files.map { it.label })
        assertEquals("Жж – ukrainian.ogg", letters.files.first().source)
    }

    // -- fingerprint exemption ---------------------------------------------------------

    @Test
    fun audioNeverEntersTheCatalogFingerprint() {
        // Recordings cannot change the join, so a refreshed pack must not stale a
        // running session — audio is read through the RAW source.
        assertEquals(Fixture.catalog().fingerprint, catalog.fingerprint)
    }
}
