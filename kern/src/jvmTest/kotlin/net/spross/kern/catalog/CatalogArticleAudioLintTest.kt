package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `articles{}` half of [CatalogAudioLintTest] over the REAL `catalog/audio/` — what a
 * recording that speaks the article may claim. A sibling file for the same reason its twin
 * is one: both stay inside the line budget that way.
 *
 * The rules the two sections share — attribution, hashes, files shipped exactly once, the
 * pack's noise shape — are asserted over every section there and are not repeated here.
 * What is left is the one thing an article recording can get wrong that no other entry can:
 * saying a gender the card does not show.
 */
class CatalogArticleAudioLintTest {
    private val catalog get() = RealCatalog.catalog

    /**
     * An article entry says an article some card in its language shows, in front of the
     * form it stands before there, and its `word` names which one: a realization's own
     * `grammar.gender` before any form it carries, or a tagged form's authored article
     * before that form (`die Lehrerin`).
     *
     * The article is pinned because a recording is the only thing that can teach a gender
     * aloud, and a wrong one teaches it wrong — 33 of the catalog's 90 rotatable `teaches`
     * disagree with their canonical word's gender, so this is not a hypothetical. The WORD
     * is free to be a synonym or a variant: a file saying "der Großvater" is a good
     * recording of "Großvater", and which card may hear it is the lookup's question, not
     * the pack's.
     */
    @Test
    fun everyArticleEntrySaysAnArticleACardShowsBeforeItsWord() {
        for ((lang, manifest) in catalog.audio) {
            if (manifest.articles.isEmpty()) continue
            val said = catalog.articledForms(lang)
            for ((form, recording) in manifest.articles) {
                val word = checkNotNull(recording.word) { "audio/$lang article \"$form\": no word recorded" }
                assertTrue(
                    (speechKey(form) to speechKey(word)) in said,
                    "audio/$lang article \"$form\": no card in $lang says \"$word\" with that article",
                )
            }
        }
    }

    /**
     * One spoken form, one sound — the words' rule, applied inside the section. Two entries
     * whose article forms collide (de `die Bank`) have no right answer, so the converter
     * resolves them rather than letting the runtime pick.
     */
    @Test
    fun noTwoArticleEntriesClaimOneSpokenForm() {
        for ((lang, manifest) in catalog.audio) {
            val byKey = manifest.articles.entries.groupBy { speechKey(checkNotNull(it.value.matches)) }
            for ((key, group) in byKey) {
                val digests = group.mapTo(mutableSetOf()) { it.value.sha256 }
                assertEquals(1, digests.size, "audio/$lang: \"$key\" is claimed by ${group.map { it.key }}")
            }
        }
    }

    /**
     * The word index's half of the collision rule: one file answers a bare lookup for the
     * word inside it, so two article entries speaking one word have the same no-right-answer
     * problem the spoken forms do, and the runtime would return neither.
     */
    @Test
    fun noTwoArticleEntriesClaimOneBareWord() {
        for ((lang, manifest) in catalog.audio) {
            val byWord = manifest.articles.entries.groupBy { speechKey(checkNotNull(it.value.word)) }
            for ((word, group) in byWord) {
                val digests = group.mapTo(mutableSetOf()) { it.value.sha256 }
                assertEquals(1, digests.size, "audio/$lang: \"$word\" is claimed by ${group.map { it.key }}")
            }
        }
    }
}
