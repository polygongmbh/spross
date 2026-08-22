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
     * An article entry speaks its realization's OWN article in front of its canonical text,
     * and nothing else. That string is the only key it can ever be reached by
     * (`spokenTargetForm`, from the article `shownArticle` allows), so an entry saying
     * anything else is bytes no card can play — and one saying a DIFFERENT article would be
     * a gender taught wrong, which is the whole thing the recordings exist to get right.
     */
    @Test
    fun everyArticleEntrySpeaksItsRealizationsArticleAndText() {
        for ((lang, manifest) in catalog.audio) {
            for ((slug, recording) in manifest.articles) {
                val raw = realization(lang, slug)
                assertTrue(raw != null, "audio/$lang: \"$slug (article)\" is not realized in $lang")
                val article = raw.grammar["gender"]
                assertTrue(!article.isNullOrBlank(), "audio/$lang/$slug: no article authored to speak")
                assertEquals(
                    speechKey(spokenTargetForm(article, raw.text, raw.text)),
                    speechKey(checkNotNull(recording.matches)),
                    "audio/$lang/$slug (article): \"${recording.matches}\" is not \"$article ${raw.text}\"",
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
     * An article recording never displaces the bare one: the source side of a pair reads
     * the learner's own language, where the article is not what is being taught, and the
     * bare file is the only one that may answer there. Shipping an article entry whose slug
     * has no bare recording would silence that side to make the target side fuller.
     */
    @Test
    fun everyArticleEntryKeepsItsBareRecordingBesideIt() {
        for ((lang, manifest) in catalog.audio) {
            for (slug in manifest.articles.keys) {
                assertTrue(slug in manifest.words, "audio/$lang/$slug: an article recording and no bare one")
            }
        }
    }
}
