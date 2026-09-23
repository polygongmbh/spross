package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The lookup half of [CatalogAudioParseTest] — which recording a visible form reaches,
 * what it is spoken as and how it is credited, over the synthetic [AudioFixture].
 */
class CatalogAudioLookupTest {
    private val catalog = AudioFixture.catalog()

    private fun recording(lang: String, form: String): String? =
        catalog.pronunciation(lang, form).recordingPath

    // -- the analysis index ------------------------------------------------------------

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
                // the article recordings are a speaker of their own, credited as one
                "de|Nina|CC BY-SA 4.0",
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
