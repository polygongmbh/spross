package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Whether a word may be said, and which branch says it. */
class SoundGateTest {

    /**
     * RULE: the mute holds autoplay and nothing the learner asked for;
     * a screen reader holds every word the screen would be talked over by.
     */
    @Test
    fun aMutedLearnerHoldsAutoplayButNotATap() {
        assertTrue(PronounceTrigger.Auto.held(muted = true, readsScreenAloud = false))
        assertFalse(PronounceTrigger.Tap.held(muted = true, readsScreenAloud = true))
        assertFalse(PronounceTrigger.Essential.held(muted = true, readsScreenAloud = false))
        assertTrue(PronounceTrigger.Essential.held(muted = false, readsScreenAloud = true))
        assertFalse(PronounceTrigger.Listening.held(muted = true, readsScreenAloud = true))
    }

    /**
     * RULE: a recording answers first unless the learner prefers the voice and there is one;
     * the voice covers what no recording matched, and the credits' recording-only ask still
     * falls back to it.
     */
    @Test
    fun theVoiceLeadsOnlyWherePreferredAndPresent() {
        assertEquals(SoundBranch.Recording, soundBranch(false, false, hasVoice = true, hasRecording = true))
        assertEquals(SoundBranch.Speech, soundBranch(true, false, hasVoice = true, hasRecording = true))
        assertEquals(SoundBranch.Recording, soundBranch(true, false, hasVoice = false, hasRecording = true))
        assertEquals(SoundBranch.Recording, soundBranch(true, recordingOnly = true, hasVoice = true, hasRecording = true))
        assertEquals(SoundBranch.Speech, soundBranch(false, false, hasVoice = true, hasRecording = false))
        assertEquals(SoundBranch.Silent, soundBranch(true, false, hasVoice = false, hasRecording = false))
    }
}
