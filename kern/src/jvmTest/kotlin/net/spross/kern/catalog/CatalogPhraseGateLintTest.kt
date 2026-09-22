package net.spross.kern.catalog

import net.spross.kern.model.CardKind
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The unlock-gate half of `catalog/README.md`'s coverage rule, sibling to
 * [CatalogLintTest] for the same reason [CatalogCollisionLintTest] is: a rule about
 * `components` specifically, kept out of the general file.
 *
 * A phrase's `components` name the same-area word cards it grows from
 * (`catalog/README.md` § `areas/<area>/concepts.json`), and coverage is deliberately
 * non-uniform (`catalog/README.md` "Coverage may be non-uniform"). Nothing else reads
 * a phrase's realized languages against its components' realized languages together, so
 * a phrase realized in a language whose component card is NOT waits forever on a card
 * that will never exist there — a no-go, silent until this test.
 *
 * [CatalogLintTest.conceptReferencesResolveSameArea] already holds that every component
 * resolves to a same-area, non-phrase concept, so a component here is always a key into
 * this SAME area's [CatalogArea.realizations].
 */
class CatalogPhraseGateLintTest {
    private val catalog get() = RealCatalog.catalog

    /**
     * For every phrase with `components`, the set of languages it is realized in must be
     * a SUBSET of the languages every one of its components is realized in.
     */
    @Test
    fun phraseComponentsCoverEveryLanguageThePhraseDoes() {
        val offenses = mutableListOf<String>()
        for (area in catalog.areas) {
            for (concept in area.concepts) {
                if (concept.kind != CardKind.Phrase || concept.components.isEmpty()) continue
                val phraseLangs = catalog.languages.keys.filter { lang ->
                    area.realizations[lang]?.containsKey(concept.slug) == true
                }
                for (component in concept.components) {
                    val missing = phraseLangs.filter { lang ->
                        area.realizations[lang]?.containsKey(component) != true
                    }
                    if (missing.isNotEmpty()) {
                        offenses += "${area.name}/${concept.slug}: gates on $component, " +
                            "which is missing in ${missing.sorted()} where the phrase itself is realized"
                    }
                }
            }
        }
        assertTrue(offenses.isEmpty(), offenses.joinToString("\n"))
    }
}
