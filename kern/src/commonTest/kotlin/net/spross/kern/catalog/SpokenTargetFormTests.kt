package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a synthesizer is handed for a target word — the article rule, said out loud. */
class SpokenTargetFormTests {

    /**
     * RULE: a target form with an authored article is spoken with it.
     * WHY: the article is grammar the learner has to hear — in German it is half of what
     * knowing a noun means, and a word met a hundred times as a bare stem is learned wrong.
     */
    @Test
    fun aGenderedTargetWordIsSpokenWithItsArticle() {
        assertEquals("das Brot", spokenTargetForm("das", "Brot", "Brot"))
        assertEquals("la casa", spokenTargetForm("la", "casa", "casa"))
    }

    /**
     * RULE: a rotated synonym is spoken bare.
     * WHY: a synonym is a different word and may carry a different gender, so the card's
     * article would mislabel it — the same reason `shownArticle` withholds it on screen.
     * Nothing beats a wrong article into a learner.
     */
    @Test
    fun aRotatedSynonymIsSpokenWithoutTheCardsArticle() {
        assertEquals("Laib", spokenTargetForm("das", "Laib", "Brot"))
    }

    /**
     * RULE: a target language with no article authored is spoken as it stands.
     * WHY: most of them have none, and an article invented for them would be taught as fact.
     */
    @Test
    fun aWordWithNoArticleIsSpokenAsItStands() {
        assertEquals("mkate", spokenTargetForm(null, "mkate", "mkate"))
        // Authoring slack, not a rule: an empty string is an absent article.
        assertEquals("mkate", spokenTargetForm("", "mkate", "mkate"))
    }

    /**
     * RULE: the article goes in front of what `utterance` would have said, not in front of
     * the raw form.
     * WHY: one question, one answer — the citation stem dash a synthesizer would vocalize
     * ("minus zuri") has to fall away whether or not an article rides in front of it.
     */
    @Test
    fun theArticleSitsInFrontOfTheSpokenForm() {
        assertEquals("zuri", spokenTargetForm(null, "-zuri", "-zuri"))
        assertEquals("die zuri", spokenTargetForm("die", "-zuri", "-zuri"))
    }

    /**
     * RULE: an elided article writes onto its noun, with no space between.
     * WHY: the apostrophe IS the join — "l' acqua" is a spelling nobody writes, and this
     * string is also what an article recording is looked up by, where Commons spells the
     * title `It-l'acqua.ogg`. One sound, so one key, whichever branch says it.
     */
    @Test
    fun anElidedArticleWritesOntoItsNoun() {
        assertEquals("l'acqua", spokenTargetForm("l'", "acqua", "acqua"))
        assertEquals("l'università", spokenTargetForm("l'", "università", "università"))
        // The curly apostrophe a catalog may pick up joins exactly as the typewriter one does.
        assertEquals("l’olio", spokenTargetForm("l’", "olio", "olio"))
    }
}
