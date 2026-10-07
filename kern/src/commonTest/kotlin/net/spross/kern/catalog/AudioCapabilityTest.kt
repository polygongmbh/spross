package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What can carry a language's sound — the pack half read off the catalog, the voice half
 * handed in.
 *
 * [AudioFixture] is shaped like the real thing for exactly this: `de` and `sw` ship manifests,
 * `en` ships none at all.
 */
class AudioCapabilityTest {

    private val catalog: Catalog = Catalog.load(MapCatalogSource(Fixture.files + AudioFixture.files))

    /**
     * RULE: a pack and a voice are named apart, and every combination of the two has a name.
     * WHY: the callers need different halves of the answer — the audio setting offers one
     * segment per SOURCE, playback picks a branch, and a tile only wants to know whether
     * anything at all can speak. One value answering all three is what keeps the rule from
     * being written again in each of them.
     */
    @Test
    fun everyCombinationOfPackAndVoiceHasAName() {
        assertEquals(AudioCapability.Both, audioCapability(catalog, "de", hasVoice = true))
        assertEquals(AudioCapability.RecordingsOnly, audioCapability(catalog, "de", hasVoice = false))
        assertEquals(AudioCapability.VoiceOnly, audioCapability(catalog, "en", hasVoice = true))
        assertEquals(AudioCapability.None, audioCapability(catalog, "en", hasVoice = false))
    }

    /**
     * RULE: the two halves are readable on their own, and only `None` is silent.
     * WHY: a surface asks one of three different questions of the same value, and a caller
     * that had to spell `== Both || == RecordingsOnly` for itself is the fifth copy of the
     * rule this type exists to delete.
     */
    @Test
    fun eachHalfIsReadableOnItsOwn() {
        assertTrue(AudioCapability.RecordingsOnly.hasRecordings && !AudioCapability.RecordingsOnly.hasVoice)
        assertTrue(AudioCapability.VoiceOnly.hasVoice && !AudioCapability.VoiceOnly.hasRecordings)
        assertEquals(listOf(AudioCapability.None), AudioCapability.entries.filter { it.silent })
    }

    /**
     * RULE: a language whose pack ships has recordings; one without a manifest does not.
     * WHY: this is the fixed half, and it is a map lookup rather than a sweep — the whole
     * point of asking it instead of walking the join to find a word that can be heard.
     */
    @Test
    fun theCatalogAnswersWhichLanguagesShipAPack() {
        assertTrue(catalog.hasRecordings("de"))
        assertFalse(catalog.hasRecordings("en"))
        // A language the catalog does not declare at all cannot have one either.
        assertFalse(catalog.hasRecordings("zz"))
    }

    /**
     * RULE: having a pack is not having a recording of a given word.
     * WHY: a pack covers the forms it recorded and no others, so the per-language answer may
     * never stand in for [audible] — a pool that filtered on it would keep words that play
     * as one dead beat inside a run.
     */
    @Test
    fun aPackDoesNotMeanEveryFormOfThatLanguageIsRecorded() {
        assertTrue(catalog.hasRecordings("de"))
        assertFalse(audible("unaufsagbar", "de", catalog, hasVoice = false))
    }

    /**
     * RULE: the audio setting offers only sources that can answer, and a stored source the
     * language cannot answer reads as the other one; muted reads as Off whatever is stored.
     * WHY: a chosen option that plays nothing is a broken promise on the one screen that
     * exists to fix the sound.
     */
    @Test
    fun theAudioSettingNeverShowsASourceThatCannotAnswer() {
        assertEquals(listOf(AudioPreference.Off, AudioPreference.Recordings),
                     AudioCapability.RecordingsOnly.preferenceOptions)
        assertEquals(AudioPreference.Recordings,
                     AudioCapability.RecordingsOnly.preference(muted = false, prefersSpeech = true))
        assertEquals(AudioPreference.Speech,
                     AudioCapability.VoiceOnly.preference(muted = false, prefersSpeech = false))
        assertEquals(AudioPreference.Off, AudioCapability.Both.preference(muted = true, prefersSpeech = true))
    }
}
