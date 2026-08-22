package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Recordings that speak the card's ARTICLE — which one answers, and which card may hear it.
 *
 * A sibling of [CatalogAudioFixtureTest] rather than more of it: that file already carries
 * the parse, index and credit rules to its line budget, and this is one rule of its own —
 * the target side hears "der Kellner", every other caller hears what it always did.
 */
class CatalogArticleAudioTest {
    private val catalog = AudioFixture.catalog()

    private fun path(form: String, article: String? = null): String? =
        catalog.pronunciation("de", form, article).recordingPath

    /**
     * The article the CARD shows is what picks the file: given one, the recording that
     * speaks it, and the article recording is never reached by any other route — a
     * lookup keyed by the bare form cannot find it, however the entry is spelled.
     */
    @Test
    fun theArticleRecordingAnswersTheCardThatShowsTheArticle() {
        assertEquals("audio/de/articles/waiter.mp3", path("Kellner", "der"))
        assertEquals("audio/de/waiter.mp3", path("Kellner"))
    }

    /**
     * A wrong article is not a near miss to be rounded off: it is a different spoken form,
     * so the bare recording answers and the learner hears no gender claim at all. The
     * shipped packs cannot mint one (lint pins each entry's article to its realization),
     * but a hand-edited manifest could, and this is where it would stop.
     */
    @Test
    fun anArticleTheRecordingDoesNotSayFallsBackToTheBareWord() {
        assertEquals("audio/de/waiter.mp3", path("Kellner", "die"))
    }

    /**
     * A rotated synonym cannot reach the canonical word's article recording, and does not
     * depend on the caller to withhold the article for that: the key IS the article plus
     * the form on the card, so "der Ober" finds nothing where the file says "der Kellner".
     * `shownArticle` already keeps a card's article off a synonym that may carry another
     * gender; this is the second lock, on the side that owns the bytes.
     */
    @Test
    fun anArticleNeverReachesAFormTheRecordingDoesNotSpeak() {
        assertNull(path("Ober", "der"))
    }

    /**
     * Coverage is partial by construction — one speaker recorded 221 of the German nouns,
     * not all of them — so a card with an article and no article recording still plays,
     * bare, rather than falling silent onto the synthesizer.
     */
    @Test
    fun aWordWithNoArticleRecordingStillPlaysItsBareOne() {
        assertEquals("audio/de/door.mp3", path("Tür", "die"))
        assertNull(path("Kellnerin", "die")) // nothing recorded either way
    }

    /**
     * The other direction, and the reason an article entry carries the bare word inside it:
     * a file that says "die Maus" is still a recording OF "Maus", so it answers a
     * card that asks without an article rather than leaving one silent. That is the LAST
     * thing tried — `Kellner` has both files, and the bare one wins a bare ask.
     */
    @Test
    fun anArticleRecordingAnswersABareAskWhenNothingElseDoes() {
        assertEquals("audio/de/articles/mouse.mp3", path("Maus"))
        assertEquals("audio/de/articles/mouse.mp3", path("Maus", "die"))
        assertEquals("audio/de/waiter.mp3", path("Kellner"))
    }

    /** The article file's measurements are its own, and travel with the path that names it. */
    @Test
    fun theArticleRecordingCarriesItsOwnIndex() {
        val spoken = catalog.pronunciation("de", "Kellner", "der")
        assertEquals(4.2, spoken.gain)
        assertEquals(2.1, spoken.gainPhone)
        assertEquals(210L, spoken.leadMs)
        // The form on the card is what is returned and what a synthesizer would say —
        // the article joins it through `spokenTargetForm`, never through the lookup.
        assertEquals("Kellner", spoken.form)
        assertEquals("Kellner", spoken.utterance)
    }

    /**
     * The credits screen names every bundled file, so a second speaker for the same word
     * earns a row of their own — BY-SA on the article recording beside BY on the bare one
     * is exactly the case a blanket notice would get wrong.
     */
    @Test
    fun theArticleRecordingIsCredited() {
        val credited = catalog.audioCredits().flatMap { credit -> credit.files.map { credit.author to it.label } }
        assertEquals(listOf("Nina" to "der Kellner", "Nina" to "die Maus"),
                     credited.filter { it.first == "Nina" })
    }
}
