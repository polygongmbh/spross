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

    private fun realization(lang: String, slug: String): RawRealization? =
        catalog.areas.firstNotNullOfOrNull { it.realizations[lang]?.get(slug) }

    /**
     * An article entry says its realization's OWN article in front of a form that
     * realization actually carries, and its `word` names which one.
     *
     * The article is pinned because a recording is the only thing that can teach a gender
     * aloud, and a wrong one teaches it wrong — 33 of the catalog's 90 rotatable synonyms
     * disagree with their canonical word's gender, so this is not a hypothetical. The WORD
     * is free to be a synonym or a variant: a file saying "der Großvater" is a good
     * recording of "Großvater", and which card may hear it is the lookup's question, not
     * the pack's.
     */
    @Test
    fun everyArticleEntrySaysItsArticleInFrontOfAFormItHas() {
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.articles) {
                val raw = realization(lang, slug)
                assertTrue(raw != null, "audio/$lang: \"$slug (article)\" is not realized in $lang")
                val article = raw.grammar["gender"]
                assertTrue(!article.isNullOrBlank(), "audio/$lang/$slug: no article authored to speak")
                val word = checkNotNull(recording.word) { "audio/$lang/$slug (article): no word recorded" }
                val forms = (listOf(raw.text) + raw.synonyms + raw.variants).map { speechKey(it) }
                assertTrue(
                    speechKey(word) in forms,
                    "audio/$lang/$slug (article): \"$word\" is none of $forms",
                )
                assertEquals(
                    speechKey(spokenTargetForm(article, word, word)),
                    speechKey(checkNotNull(recording.matches)),
                    "audio/$lang/$slug (article): \"${recording.matches}\" is not \"$article $word\"",
                )
            }
        }
    }

    /** Article files live beside the bare ones under their own folder, slug-named as those are. */
    @Test
    fun articleFilesAreNamedAfterTheirSlug() {
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.articles) {
                assertEquals("articles/$slug.mp3", recording.file, "audio/$lang/$slug (article): misnamed file")
            }
        }
    }

    /**
     * One spoken form, one sound — the words' rule, applied inside the section. Two slugs
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
