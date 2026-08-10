package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.catalog.VoiceSelection.Candidate

/** Which voice speaks a language — the variety first, the device's own ranking second. */
class VoiceSelectionTest {

    /** Spanish is taught with distinción; a bare "es" would let a device answer in seseo. */
    @Test
    fun spanishAsksForThePeninsularVariety() {
        assertEquals("es-ES", VoiceSelection.preferredTag("es"))
    }

    @Test
    fun everyOtherLanguageAsksForItselfAlone() {
        assertEquals("sw", VoiceSelection.preferredTag("sw"))
        assertEquals("uk", VoiceSelection.preferredTag("uk"))
        assertEquals("de", VoiceSelection.preferredTag("de"))
    }

    @Test
    fun aRegionalVoiceStillAnswersForTheBareCode() {
        val austrian = Candidate("de-AT", quality = 2, identifier = "at")
        assertEquals(austrian, VoiceSelection.select("de", listOf(austrian)))
    }

    /** Tag case is a platform habit, not a distinction. */
    @Test
    fun tagCaseIsNotAMeaning() {
        val shouty = Candidate("DE-AT", quality = 2, identifier = "at")
        assertEquals(shouty, VoiceSelection.select("de", listOf(shouty)))
        val peninsular = Candidate("ES-es", quality = 1, identifier = "b")
        assertEquals(
            peninsular,
            VoiceSelection.select("es", listOf(Candidate("es-MX", 3, "a"), peninsular)),
        )
    }

    @Test
    fun theBetterVoiceWins() {
        val plain = Candidate("de-DE", quality = 1, identifier = "b")
        val enhanced = Candidate("de-DE", quality = 3, identifier = "a")
        assertEquals(enhanced, VoiceSelection.select("de", listOf(plain, enhanced)))
    }

    /** A tie has to break somewhere stable, or a word stops sounding like itself twice. */
    @Test
    fun aTieGoesToTheLowerIdentifier() {
        val second = Candidate("de-DE", quality = 1, identifier = "b")
        val first = Candidate("de-DE", quality = 1, identifier = "a")
        assertEquals(first, VoiceSelection.select("de", listOf(second, first)))
    }

    /** The variety outranks quality: a better Latin-American voice would teach the wrong sound. */
    @Test
    fun aPeninsularVoiceBeatsABetterLatinAmericanOne() {
        val mexican = Candidate("es-MX", quality = 3, identifier = "a")
        val peninsular = Candidate("es-ES", quality = 1, identifier = "b")
        assertEquals(peninsular, VoiceSelection.select("es", listOf(mexican, peninsular)))
    }

    /** Narrowing is a preference, not a requirement — a device with no es-ES still speaks. */
    @Test
    fun spanishFallsBackToWhateverSpanishTheDeviceHas() {
        val mexican = Candidate("es-MX", quality = 3, identifier = "a")
        assertEquals(mexican, VoiceSelection.select("es", listOf(mexican)))
    }

    @Test
    fun aDeviceWithoutTheLanguageOffersNoVoice() {
        assertNull(VoiceSelection.select("de", listOf(Candidate("fr-FR", 3, "a"))))
        assertNull(VoiceSelection.select("de", emptyList()))
        // "de" must not swallow "dens" — a region follows a hyphen or it is another language.
        assertNull(VoiceSelection.select("de", listOf(Candidate("den", 3, "a"))))
    }
}
