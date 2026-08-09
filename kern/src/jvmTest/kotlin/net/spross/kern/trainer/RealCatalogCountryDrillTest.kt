package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.RealCatalog

/**
 * The drill run against the SHIPPING atlas — rules only, never a pinned draw. What a
 * seeded run produces over real content is an authoring fact (the exonyms are still owed a
 * native sweep), so the goldens live on the synthetic fixture and these assertions state
 * what must survive every content edit, in every direction the app can be pointed.
 */
class RealCatalogCountryDrillTest {
    private val catalog get() = RealCatalog.catalog

    private fun pairs(): List<Pair<String, String>> =
        catalog.languages.keys.flatMap { source ->
            catalog.languages.keys.filter { it != source }.map { source to it }
        }

    @Test
    fun everyPairOpensOnItsOwnCountries() {
        for ((source, target) in pairs()) {
            val content = assertNotNull(
                catalog.countryDrillContent(source, target),
                "$source→$target: no atlas",
            )
            assertTrue(
                CountryDrill.tasks(content, 1).isNotEmpty(),
                "$source→$target: rung 1 has nothing to ask",
            )
        }
    }

    /**
     * The names the two languages share are filtered out of the name questions, and no
     * real pair loses its opening rung to that (which is what the fallback would rescue —
     * it must stay unreached, or a pair is silently drilling "Venezuela" → "Venezuela").
     *
     * A plain lowercase comparison, deliberately weaker than the accent-blind rule the
     * drill filters on: this states what a learner must never see, not how it is computed.
     */
    @Test
    fun noRungAsksForAnAnswerItsOwnPromptAlreadyGives() {
        for ((source, target) in pairs()) {
            val content = assertNotNull(catalog.countryDrillContent(source, target))
            for (level in 1..CountryDrill.MAX_LEVEL) {
                for (reverse in listOf(false, true)) {
                    val named = CountryDrill.tasks(content, level, reverse)
                        .filter { it.kind == CountryTaskKind.CountryName }
                    assertTrue(named.isNotEmpty(), "$source→$target rung $level: no name question left")
                    for (task in named) {
                        assertTrue(
                            task.accepted.none { it.lowercase() == task.promptText.lowercase() },
                            "$source→$target rung $level: ${task.id} asks for the name it shows",
                        )
                    }
                }
            }
        }
    }

    /**
     * Two hundred draws a rung, both directions, every pair: what this catches is a pool
     * that empties out or a task the builder cannot finish — the failures that would land
     * mid-run rather than on the overview.
     */
    @Test
    fun everyRungSamplesCleanlyInBothDirections() {
        for ((source, target) in pairs()) {
            val content = assertNotNull(catalog.countryDrillContent(source, target))
            for (level in 1..CountryDrill.MAX_LEVEL) {
                for (reverse in listOf(false, true)) {
                    val rng = Random(level.toLong())
                    var last: String? = null
                    repeat(200) {
                        val task = CountryDrill.sample(content, level, reverse, last, rng)
                        assertTrue(
                            task.promptText.isNotBlank() && task.display.isNotBlank(),
                            "$source→$target rung $level: blank question",
                        )
                        assertTrue(
                            task.display in task.accepted,
                            "$source→$target rung $level: ${task.id} does not accept its own reveal",
                        )
                        last = task.id
                    }
                }
            }
        }
    }

    /** The table renders from the joined rows, so it can never list what the drill cannot ask. */
    @Test
    fun theReferenceTableCoversEveryJoinedCountry() {
        for ((source, target) in pairs()) {
            val content = assertNotNull(catalog.countryDrillContent(source, target))
            val listed = CountryDrill.reference(content).flatMap { group -> group.rows.map { it.slug } }
            assertTrue(
                listed.toSet() == content.countries.map { it.slug }.toSet(),
                "$source→$target: the reference table and the join disagree",
            )
        }
    }
}
