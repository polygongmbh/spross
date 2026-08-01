package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun everyAuthoredFieldLandsWhereItWasAuthored() {
        val word = catalog.audio.getValue("uk").words.getValue("mouse")
        assertEquals("mouse.mp3", word.file)
        assertEquals("миша", word.matches)
        assertEquals("CC BY 3.0 us", word.licence)
        assertEquals("https://creativecommons.org/licenses/by/3.0/us/", word.licenceUrl)
        assertEquals("Halyna", word.author)
        assertEquals("Uk-миша.ogg", word.source)
        assertEquals("u2", word.sha256)
        // A letter carries the same shape minus the spoken form.
        val letter = catalog.audio.getValue("uk").letters.getValue("ж")
        assertEquals("letters/u0436.mp3", letter.file)
        assertNull(letter.matches)
        assertEquals("Tabrus", letter.author)
        // Public-domain files have no deed to link.
        assertNull(catalog.audio.getValue("uk").words.getValue("door").licenceUrl)
    }

    @Test
    fun unknownKeysAreRejectedWithTheirPath() {
        val error = assertFailsWith<CatalogFormatException> {
            AudioFixture.catalogWith(
                "audio/uk/manifest.json",
                """
                { "language": "uk",
                  "words": { "mouse": { "file": "mouse.mp3", "matches": "миша", "licence": "Public domain",
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
                  "letters": { "ж": { "file": "letters/u0436.mp3", "matches": "же", "licence": "Public domain",
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
        assertEquals("ім'я", speechKey("ім'я")) // an INNER apostrophe is part of the word
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
    fun creditsGroupPerLanguageAuthorAndLicence() {
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
            credits.map { "${it.language}|${it.author}|${it.licence}" },
        )
        assertEquals(
            listOf(AudioCreditFile("kochen", "De-kochen.ogg"), AudioCreditFile("Tür", "De-Tür.ogg")),
            credits.first().files,
        )
        assertNull(credits.first { it.licence == "Public domain" }.licenceUrl)
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
