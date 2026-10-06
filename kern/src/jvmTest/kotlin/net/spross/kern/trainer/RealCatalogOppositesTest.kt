package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.catalog.RealCatalog

/** The opposites drill against the SHIPPING catalog, every card past every bar. */
class RealCatalogOppositesTest {

    private fun report(source: String, target: String): OppositesAvailability.Report {
        val catalog = RealCatalog.catalog
        return OppositesAvailability.report(ScrambleFixture.box(catalog.join(source, target)), catalog.oppositePairs)
    }

    private fun answers(report: OppositesAvailability.Report, form: String): Set<String> =
        report.prompts.single { it.form == form }.opposites.map { it.text }.toSet()

    @Test
    fun everyLanguageFillsTheDrill() {
        for (target in listOf("de", "en", "eo", "es", "fr", "it", "sw", "uk")) {
            val source = if (target == "en") "de" else "en"
            assertTrue(report(source, target).drillAvailable, "$target has too few pairs")
        }
    }

    /** The merges the drill is there to show stay merged in the catalog. */
    @Test
    fun aMergedWordAsksForEveryOpposite() {
        assertEquals(setOf("einziehen", "anziehen"), answers(report("en", "de"), "ausziehen"))
        assertEquals(setOf("kushoto", "kucheka"), answers(report("en", "sw"), "kulia"))
    }
}
