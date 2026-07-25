package net.spross.kern.trainer

import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Language
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Template vocab audit against the REAL catalog: every non-slot content word
 * in each targetTemplate (and every counted-noun form) must be VERIFIED
 * vocabulary — verbatim in the join(de, target) card texts, an inflected form
 * of a joined word (documented map), or one of the documented
 * function/international words below.
 */
class PhraseVocabAuditTests {

    private val targets = listOf("sw", "uk")

    /** Documented allowlist — function words and international words only. */
    private val allowlist: Map<Language, Set<String>> = mapOf(
        "sw" to setOf(
            "tuna",  // tu-na „wir haben“: Subjektpräfix tu- + Possessiv-na (Funktionskonstruktion)
            "tangu", // Präposition „seit“
            "euro",  // internationale Währung, „Euro“ auf beiden Seiten
            "mwaka", // „Jahr“ — Kopfnomen der Jahresangabe (tangu mwaka …)
        ),
        "uk" to setOf(
            "нас",   // Personalpronomen „uns“ («у нас є» = wir haben)
            "повторіть", // „wiederholen Sie“ — grounded in the basics starter pack
            "євро",  // internationale Währung, unveränderlich
        ),
    )

    /**
     * Inflected template form → catalog lemma. The lemma itself must be
     * verbatim in the join (asserted below), so this documents inflection,
     * never new vocabulary.
     */
    private val inflectionMap: Map<Language, Map<String, String>> = mapOf(
        "sw" to mapOf(
            "ninaamka" to "kuamka",   // ni-na-amka „ich wache auf“
            "tunakula" to "kula",     // tu-na-kula „wir essen“
            "rudia" to "kurudia",     // Imperativ „wiederhole“
            "andika" to "kuandika",   // Imperativ „schreib“
        ),
        "uk" to mapOf(
            "будильнику" to "будильник", // Lokativ nach «на»
            "напиши" to "писати",        // Imperativ „schreib“
            "зошити" to "зошит", "зошитів" to "зошит",       // Zählformen
            "стільці" to "стілець", "стільців" to "стілець", // Zählformen
            "ключів" to "ключ",                              // Zählform (ключі steht verbatim im Katalog)
        ),
    )

    @Test
    fun targetTemplateWordsAreVerifiedVocabulary() {
        for (target in targets) {
            val seedWords = joinTargetWords(target)
            val allow = allowlist[target].orEmpty()
            val inflections = inflectionMap[target].orEmpty()

            for (template in PhraseTemplates.templates(source = "de", target = target)) {
                var text = template.targetTemplate
                    .replace("{slot}", " ")
                    .replace("{count}", " ")
                template.countForms?.let { forms ->
                    text += " ${forms.one} ${forms.few} ${forms.many}"
                }
                for (word in tokens(text)) {
                    val verified = word in seedWords ||
                        word in allow ||
                        inflections[word]?.let { it in seedWords } == true
                    assertTrue(verified, "${template.id}: „$word“ is not verified catalog vocabulary")
                }
            }
        }
    }

    @Test
    fun inflectionLemmasAndAllowlistStaySmallAndGrounded() {
        for (target in targets) {
            val seedWords = joinTargetWords(target)
            for ((form, lemma) in inflectionMap[target].orEmpty()) {
                assertTrue(lemma in seedWords, "lemma „$lemma“ (for „$form“) missing from join(de, $target)")
            }
            assertTrue(allowlist[target].orEmpty().size <= 4, "allowlist must stay small")
        }
    }

    // Catalog extraction

    /**
     * All target-language words a de-source learner of [target] can study:
     * area titles plus text/synonyms/variants of every joined card.
     */
    private fun joinTargetWords(target: Language): Set<String> {
        val catalog = RealCatalog.catalog
        val words = mutableSetOf<String>()
        for (area in catalog.areaNames) {
            catalog.areaTitle(area, target)?.let { words += tokens(it) }
        }
        for (card in catalog.join(source = "de", target = target)) {
            words += tokens(card.target.text)
            card.target.synonyms.forEach { words += tokens(it) }
            card.target.variants.forEach { words += tokens(it) }
        }
        return words
    }

    /**
     * Lowercase word tokens; apostrophes/hyphens dropped in-word
     * (mirrors AnswerNormalizer), all other non-letters split.
     */
    private fun tokens(text: String): List<String> {
        val joined = text.lowercase().filter { it != '\'' && it != '’' && it != '-' }
        return joined
            .map { if (it.isLetter()) it else ' ' }
            .joinToString("")
            .split(" ")
            .filter { it.isNotEmpty() }
    }
}
