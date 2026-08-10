package net.spross.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.model.EmojiCue

/**
 * When the card's picture slot fills. Kern decides WHICH cue a word gets; this is the
 * one rule the screen adds on top, and it is pinned here because both roles read it.
 */
class EmojiSlotTest {

    @Test
    fun theUpfrontCueShowsFromTheFirstFrame() {
        assertTrue(emojiShowing(EmojiCue.Upfront, revealed = false))
        assertTrue(emojiShowing(EmojiCue.Upfront, revealed = true))
    }

    @Test
    fun theHeldBackCueArrivesWithTheAnswerAndNotBefore() {
        assertFalse(emojiShowing(EmojiCue.OnReveal, revealed = false))
        assertTrue(emojiShowing(EmojiCue.OnReveal, revealed = true))
    }

    @Test
    fun aWordWithNoPictureNeverFillsASlot() {
        assertFalse(emojiShowing(null, revealed = false))
        assertFalse(emojiShowing(null, revealed = true))
    }
}
