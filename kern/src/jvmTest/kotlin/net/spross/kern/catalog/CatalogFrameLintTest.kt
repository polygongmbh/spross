package net.spross.kern.catalog

import net.spross.kern.trainer.Trainer
import net.spross.kern.trainer.TrainerKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Permanent lint over the REAL `catalog/phrases/`, beside [CatalogLintTest] the way
 * [CatalogAudioLintTest] and [AlphabetLintTest] sit beside it. The parser enforces the same
 * rules on load; pinning them here is what makes a regression in either one loud.
 */
class CatalogFrameLintTest {
    private val catalog get() = RealCatalog.catalog
    private val slugPattern = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    private val slots get() = catalog.frames.associate { it.slug to it.slot }

    private fun forEachFrame(action: (lang: String, slug: String, frame: RawFrame) -> Unit) {
        for ((lang, frames) in catalog.frameRealizations) {
            for ((slug, frame) in frames) action(lang, slug, frame)
        }
    }

    private fun occurrences(text: String, marker: String): Int = text.split(marker).size - 1

    @Test
    fun framesParseAndEveryOneIsRealizedSomewhere() {
        assertTrue(catalog.frames.isNotEmpty(), "no frames in phrases/frames.json")
        val realized = catalog.frameRealizations.values.flatMap { it.keys }.toSet()
        for (frame in catalog.frames) {
            assertTrue(frame.slug in realized, "${frame.slug}: declared but realized in no language")
        }
    }

    /**
     * Frame slugs live in the concept namespace: one string must never address both a card
     * and a drill, or a schedule and a frame would answer to the same name.
     */
    @Test
    fun frameSlugsAreWellFormedUniqueAndDisjointFromConcepts() {
        val conceptSlugs = catalog.areas.flatMap { it.concepts }.map { it.slug }.toSet()
        val declared = catalog.frames.map { it.slug }
        assertEquals(declared.toSet().size, declared.size, "duplicate frame slug")
        for (slug in declared) {
            assertTrue(slugPattern.matches(slug), "bad frame slug: $slug")
            assertTrue(slug !in conceptSlugs, "frame slug \"$slug\" also names a concept")
        }
        forEachFrame { lang, slug, _ ->
            assertTrue(slug in slots, "phrases/$lang.json: realization for unknown frame \"$slug\"")
        }
    }

    /** The Trainer fills one value per frame: a second `{slot}` has nothing to go in it. */
    @Test
    fun everyFrameAndVariantCarriesExactlyOneSlot() {
        forEachFrame { lang, slug, frame ->
            for (text in listOf(frame.text) + frame.variants) {
                val where = "phrases/$lang.json $slug"
                assertTrue(text.isNotBlank() && text.trim() == text, "$where: untrimmed \"$text\"")
                assertEquals(1, occurrences(text, "{slot}"), "$where: \"$text\" slot count")
            }
            assertTrue(frame.text !in frame.variants, "phrases/$lang.json $slug: variant equals text")
        }
    }

    /**
     * `{count}` and `count` are one fact authored in two places, and agreement only has a
     * numeral to agree with on a `numbers` frame.
     */
    @Test
    fun countMarkerAndFormsImplyEachOtherOnNumbersFramesOnly() {
        forEachFrame { lang, slug, frame ->
            val where = "phrases/$lang.json $slug"
            val expected = if (frame.count == null) 0 else 1
            for (text in listOf(frame.text) + frame.variants) {
                assertEquals(expected, occurrences(text, "{count}"), "$where: \"$text\" count marker")
            }
            if (frame.count != null) {
                assertEquals(TrainerKind.Numbers, slots[slug], "$where: count on a non-numbers frame")
            }
        }
    }

    /**
     * The reference page's prose is authored per language and picked by the reader, so an
     * unknown key is prose nobody will ever see. English is required of every language the
     * Trainer can generate: it is the fallback every other reader lands on, and without it
     * that language's overview shows a heading with nothing under it.
     */
    @Test
    fun numberNotesAreReaderKeyedProseAndEveryDrillableLanguageAuthorsEnglish() {
        for ((lang, byReader) in catalog.drillNotes) {
            for ((reader, lines) in byReader) {
                val where = "phrases/$lang.json numberNotes.$reader"
                assertTrue(reader in catalog.languages, "$where: unknown reader")
                assertTrue(lines.isNotEmpty(), "$where: no lines")
                for (line in lines) {
                    assertTrue(line.isNotBlank() && line.trim() == line, "$where: untrimmed \"$line\"")
                }
            }
        }
        for (lang in catalog.languages.keys.filter { Trainer.supports(it) }) {
            assertTrue(
                catalog.numberNotes(lang, Catalog.FALLBACK_SOURCE).isNotEmpty(),
                "phrases/$lang.json: no English number notes, so every reader's overview is empty",
            )
        }
    }

    /**
     * A variant is graded against the same sentence the canonical frame produces, so the two
     * must agree on naming the language: a variant that spells one language out while the
     * text resolves the profile's would silently accept "learning German" from a Swahili
     * learner — the exact bug the marker exists to remove.
     */
    @Test
    fun textAndVariantsAgreeOnCarryingALanguageMarker() {
        forEachFrame { lang, slug, frame ->
            val where = "phrases/$lang.json $slug"
            val marked = LanguageNames.hasLanguageMarker(frame.text)
            for (variant in frame.variants) {
                assertEquals(
                    marked,
                    LanguageNames.hasLanguageMarker(variant),
                    "$where: \"$variant\" disagrees with \"${frame.text}\" on the language marker",
                )
            }
        }
    }

    /**
     * The pair join is where a marker resolves, so a frame that names the language must do
     * it in EVERY pair it otherwise joins — otherwise a language table gap silently thins
     * the drill instead of failing anywhere.
     */
    @Test
    fun markedFramesJoinEveryPairTheirRealizationsAllow() {
        val marked = catalog.frameRealizations.mapValues { (_, frames) ->
            frames.filterValues { LanguageNames.hasLanguageMarker(it.text) }.keys
        }
        for ((source, sourceMarked) in marked) {
            for ((target, targetMarked) in marked) {
                if (source == target || !Trainer.supports(target)) continue
                val joined = catalog.phraseTemplates(source, target).map { it.id }.toSet()
                for (slug in sourceMarked intersect targetMarked) {
                    assertTrue(slug in joined, "$source→$target: \"$slug\" dropped — no name for $target")
                }
            }
        }
    }

    @Test
    fun frameNotesKeyedByDeclaredLanguages() {
        forEachFrame { lang, slug, frame ->
            assertTrue(
                frame.notes.keys.all { it in catalog.languages },
                "phrases/$lang.json $slug: unknown note key ${frame.notes.keys}",
            )
        }
    }
}
