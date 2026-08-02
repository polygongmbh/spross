package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Language

/**
 * What moving the frames into the catalog was for: a frame authored once lights up every
 * pair that realizes it on both sides, and no pair that used to exist lost anything.
 *
 * The expectation is derived from the catalog (which language realizes which slug), never
 * pinned per pair — authoring a frame moves this sweep instead of breaking it. The one
 * pinned list is the pre-series hardcoded table, because that is a migration claim and it
 * must not drift.
 */
class PhrasePairCoverageTests {

    private val catalog get() = RealCatalog.catalog

    private fun realized(lang: Language): Set<String> = catalog.frameRealizations[lang].orEmpty().keys

    /** The availability rule, relationally: shared realizations, gated on the answer's pack. */
    @Test
    fun everyPairRealizingAFrameOnBothSidesJoinsIt() {
        var drillablePairs = 0
        for (source in catalog.languages.keys) {
            for (target in catalog.languages.keys) {
                if (source == target) continue
                val shared = realized(source) intersect realized(target)
                val expected = if (Trainer.supports(target)) shared else emptySet()
                assertEquals(
                    expected,
                    catalog.phraseTemplates(source, target).map { it.id }.toSet(),
                    "$source→$target",
                )
                if (expected.isNotEmpty()) drillablePairs++
            }
        }
        // Every language that authors frames at all drills against every other one, both
        // ways round — the whole point of storing nothing pair-shaped.
        val authored = catalog.languages.keys.count { realized(it).isNotEmpty() }
        assertEquals(authored * (authored - 1), drillablePairs, "a frame-authored pair drills nothing")
    }

    /** The headline: nobody ever typed this pair out, and it drills. */
    @Test
    fun englishToUkrainianDrillsWithoutAnyoneAuthoringThePair() {
        assertTrue(catalog.phraseTemplates("en", "uk").isNotEmpty())
    }

    /**
     * The 21 hardcoded entries the series migrated, by their new slugs. Subset, not equality:
     * a language may gain frames, it may never silently drop one it already taught.
     */
    @Test
    fun theMigratedPairsStillCarryEveryFrameTheyHad() {
        val deSw = setOf(
            "train-departs-at", "bus-arrives-at", "i-wake-up-at", "we-eat-at", "meeting-is-at",
            "we-have-n-plates", "it-costs-n-euros", "repeat-please", "write-please",
            "learning-since-year", "write-the-year",
        )
        val deUk = setOf(
            "it-is-now", "alarm-clock-shows", "repeat-please", "write-please", "it-costs-n-euros",
            "i-have-n-notebooks", "we-have-n-chairs", "i-have-n-keys",
            "repeat-the-year", "write-the-year",
        )
        for ((target, migrated) in listOf("sw" to deSw, "uk" to deUk)) {
            val joined = catalog.phraseTemplates("de", target).map { it.id }.toSet()
            assertEquals(emptySet(), migrated - joined, "de→$target lost migrated frames")
        }
    }

    /**
     * Prompt side and answer side really are the two languages asked for: an English
     * sentence carrying the digits, a Ukrainian sentence in every accepted reading.
     */
    @Test
    fun englishPromptsCarryDigitsAndUkrainianAnswersCarryCyrillic() {
        val templates = catalog.phraseTemplates("en", "uk")
        assertTrue(templates.isNotEmpty())
        for (template in templates) {
            val task = PhraseSlots.sample(template, Random(7))
            assertEquals("uk", task.language, template.id)
            assertTrue(task.prompt.any { it.isDigit() }, "${template.id}: \"${task.prompt}\"")
            assertTrue(task.prompt.none(::isCyrillic), "${template.id}: \"${task.prompt}\"")
            assertTrue(task.display.any(::isCyrillic), "${template.id}: \"${task.display}\"")
            assertTrue(task.display in task.accepted, template.id)
            for (accepted in task.accepted) {
                assertTrue(accepted.any(::isCyrillic), "${template.id}: \"$accepted\"")
            }
        }
    }

    private fun isCyrillic(c: Char): Boolean = c in 'Ѐ'..'ӿ'
}
