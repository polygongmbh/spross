package net.spross.kern.trainer

import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Frame vocab audit against the REAL catalog. The join is symmetric, so EVERY language with
 * a trainer pack is an answer side: for each joined pair, every non-slot content word the
 * answer DISPLAYS (canonical frame + counted-noun forms) must be VERIFIED vocabulary —
 * verbatim in the join's card texts, an inflected form of a joined word (documented map), or
 * one of the documented function/international words below. Variant frames are accept-only
 * and out of scope: a learner is never asked to produce one, which is what lets a re-cut
 * frame keep its superseded wording graded.
 *
 * Append a language by adding its block to [allowlist] and [inflectionMap]; each entry needs
 * a comment saying why the word cannot simply be a card.
 */
class PhraseVocabAuditTests {

    /**
     * Per-language allowlist size, authored — not a bound. Adding a word means editing this
     * number in the same diff, which is the forcing function: the size is reviewed, never
     * drifted into. A language with more function words than another gets a bigger figure
     * and has to say so here.
     */
    private val allowlistSize: Map<Language, Int> =
        mapOf("de" to 11, "en" to 3, "es" to 1, "fr" to 1, "it" to 2, "sw" to 4, "uk" to 1, "eo" to 1)

    /** Documented allowlist — function words and international words only. */
    private val allowlist: Map<Language, Set<String>> = mapOf(
        // --- German ---------------------------------------------------------------
        "de" to setOf(
            "der", "das",        // Artikel — der Katalog führt das Genus als Grammatikfeld
            "um", "seit",        // Präpositionen
            "ist", "haben", "habe", // Kopula sein + Possessiv haben
            "auf", "ab",         // trennbare Verbpartikeln (wache … auf, fährt … ab)
            "euro",              // internationale Währung, wie in sw/uk
            "kilo",              // internationale Maßeinheit, in de/en/es dasselbe Wort — wie euro
        ),
        // --- English ---------------------------------------------------------------
        "en" to setOf(
            "euros", // internationale Währung, wie in de/sw/uk — im Plural, weil „euro“ selbst kein Kartenwort ist
            "been",  // Hilfsverb: „since“ erzwingt have been + -ing, ein Präsens ist hier ungrammatisch
            "kilo",  // internationale Maßeinheit, wie in de/es
        ),
        // --- Spanish ---------------------------------------------------------------
        "es" to setOf(
            "kilo", // internationale Maßeinheit, wie in de/en
        ),
        // --- French ---------------------------------------------------------------
        "fr" to setOf(
            "kilo", // internationale Maßeinheit, wie in de/en/es
        ),
        // --- Italian ---------------------------------------------------------------
        "it" to setOf(
            "euro",  // internationale Währung, im Plural unverändert — wie in de/sw/uk
            "chilo", // internationale Maßeinheit, wie in de/en/es
        ),
        // --- Swahili --------------------------------------------------------------
        "sw" to setOf(
            "tuna",  // tu-na „wir haben“: Subjektpräfix tu- + Possessiv-na (Funktionskonstruktion)
            "tangu", // Präposition „seit“
            "euro",  // internationale Währung, „Euro“ auf beiden Seiten
            "mwaka", // „Jahr“ — Kopfnomen der Jahresangabe (tangu mwaka …)
        ),
        // --- Ukrainian ------------------------------------------------------------
        "uk" to setOf(
            "євро",  // internationale Währung, unveränderlich
        ),
        // --- Esperanto ------------------------------------------------------------
        "eo" to setOf(
            "kilogramo", // internationale Maßeinheit, wie „kilo“ in de/en/es
        ),
    )

    /**
     * Inflected frame form → catalog lemma. The lemma itself must be verbatim in the join
     * (asserted below), so this documents inflection, never new vocabulary.
     */
    private val inflectionMap: Map<Language, Map<String, String>> = mapOf(
        // --- German ---------------------------------------------------------------
        "de" to mapOf(
            "fährt" to "fahren", "kommt" to "kommen",   // 3. Person Singular
            "wache" to "aufwachen", "lerne" to "lernen", // 1. Person Singular
            "zeigt" to "zeigen",
            "schreib" to "schreiben",                    // Imperativ
            "brauche" to "brauchen",
            "hefte" to "Heft", "stühle" to "Stuhl",      // Plural
        ),
        // --- English ---------------------------------------------------------------
        "en" to mapOf(
            "departs" to "depart", "arrives" to "arrive",  // 3. Person Singular
            "shows" to "show", "costs" to "cost",
            "plates" to "plate", "notebooks" to "notebook", "chairs" to "chair", // Plural
        ),
        // --- Spanish ---------------------------------------------------------------
        "es" to mapOf(
            "repita" to "repetir",       // Imperativ der Höflichkeitsform (usted)
            "escribe" to "escribir",     // Imperativ der Du-Form
            "necesito" to "necesitar",   // 1. Person Singular
        ),
        // --- French ---------------------------------------------------------------
        "fr" to mapOf(
            "réveille" to "réveiller",  // reflexives se réveiller, 1. Person Singular
            "répétez" to "répéter",     // Höflichkeitsimperativ
            "écrivez" to "écrire",      // Höflichkeitsimperativ
        ),
        // --- Italian ---------------------------------------------------------------
        "it" to mapOf(
            "ripeti" to "ripetere", "scrivi" to "scrivere", // Imperativ der Du-Form
            "abbiamo" to "avere",                            // 1. Person Plural
            "taccuini" to "taccuino", "sedie" to "sedia",    // Plural
        ),
        // --- Swahili --------------------------------------------------------------
        "sw" to mapOf(
            "ninaamka" to "kuamka",   // ni-na-amka „ich wache auf“
            "tunakula" to "kula",     // tu-na-kula „wir essen“
            "rudia" to "kurudia",     // Imperativ „wiederhole“
            "andika" to "kuandika",   // Imperativ „schreib“
            // Klassenplurale, im Katalog als grammar.plural geführt (living/desk)
            "viti" to "kiti", "madaftari" to "daftari",
        ),
        // --- Ukrainian ------------------------------------------------------------
        "uk" to mapOf(
            "будильнику" to "будильник", // Lokativ nach «на»
            "напиши" to "писати",        // Imperativ „schreib“
            "повторіть" to "повторити",  // Höflichkeitsimperativ
            "дату" to "дата",            // Akkusativ nach «Повторіть/Напиши … дату»
            "зошити" to "зошит", "зошитів" to "зошит",       // Zählformen
            "стільці" to "стілець", "стільців" to "стілець", // Zählformen
            "ключів" to "ключ",                              // Zählform (ключі steht verbatim im Katalog)
            "нас" to "ми",                                   // Genitiv nach «у нас є» = wir haben
        ),
        // --- Esperanto ------------------------------------------------------------
        "eo" to mapOf(
            "alvenas" to "alveni", "vekiĝas" to "vekiĝi", // Präsens zum I-Verb des Katalogs
            "bakas" to "baki",
            "daton" to "dato", "panon" to "pano",         // Akkusativ des Objekts
        ),
    )

    @Test
    fun answerFrameWordsAreVerifiedVocabulary() {
        val unverified = mutableListOf<String>()
        for ((source, target) in joinedPairs()) {
            val seedWords = joinTargetWords(source, target)
            val allow = allowlist[target].orEmpty()
            val inflections = inflectionMap[target].orEmpty()

            for (template in RealFrames.of(source, target)) {
                for (word in frameWords(template)) {
                    val verified = word in seedWords ||
                        word in allow ||
                        inflections[word]?.let { lemma -> tokens(lemma).all { it in seedWords } } == true
                    if (!verified) unverified += "$source→$target ${template.id}: „$word“"
                }
            }
        }
        assertTrue(
            unverified.isEmpty(),
            "not verified catalog vocabulary:\n" + unverified.distinct().joinToString("\n"),
        )
    }

    @Test
    fun inflectionLemmasAndAllowlistStayGrounded() {
        val ungrounded = mutableListOf<String>()
        for ((source, target) in joinedPairs()) {
            val seedWords = joinTargetWords(source, target)
            for ((form, lemma) in inflectionMap[target].orEmpty()) {
                if (tokens(lemma).none { it in seedWords }) {
                    ungrounded += "lemma „$lemma“ (for „$form“) missing from join($source, $target)"
                }
            }
        }
        assertTrue(ungrounded.isEmpty(), ungrounded.distinct().joinToString("\n"))
        for ((lang, words) in allowlist) {
            assertEquals(
                allowlistSize[lang], words.size,
                "$lang allowlist changed size — justify the entry and update allowlistSize",
            )
        }
    }

    /** A pack-less language never answers, and a language nobody realizes never joins. */
    @Test
    fun everyLanguageWithAPackAndFramesIsAudited() {
        val audited = joinedPairs().map { it.second }.toSet()
        assertTrue(
            listOf("de", "en", "eo", "es", "fr", "it", "sw", "uk").all { it in audited },
            "audited: $audited",
        )
        for (target in audited) assertTrue(Trainer.supports(target), "$target answers without a pack")
    }

    // Catalog extraction

    private fun joinedPairs(): List<Pair<Language, Language>> {
        val languages = RealCatalog.catalog.languages.keys
        return languages.flatMap { source ->
            languages.filter { it != source && RealFrames.of(source, it).isNotEmpty() }
                .map { source to it }
        }
    }

    /** Every word the answer side DISPLAYS: the canonical frame plus the count forms. */
    private fun frameWords(template: PhraseTemplate): List<String> {
        var text = template.targetTemplate.replace("{slot}", " ").replace("{count}", " ")
        template.countForms?.let { text += " ${it.one} ${it.few} ${it.many}" }
        return tokens(text)
    }

    /**
     * All target-language words a [source] learner of [target] can study:
     * area titles plus text/synonyms/variants of every joined card.
     */
    private fun joinTargetWords(source: Language, target: Language): Set<String> {
        val catalog = RealCatalog.catalog
        val words = mutableSetOf<String>()
        for (area in catalog.areaNames) {
            catalog.areaTitle(area, target)?.let { words += tokens(it) }
        }
        for (card in catalog.join(source = source, target = target)) {
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
