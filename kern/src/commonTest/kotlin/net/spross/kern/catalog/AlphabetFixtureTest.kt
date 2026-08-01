package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Alphabet parsing and example resolution over the synthetic [AlphabetFixture]. */
class AlphabetFixtureTest {
    private val catalog = AlphabetFixture.catalog()
    private val de = catalog.alphabet("de") ?: throw AssertionError("no de alphabet")
    private val uk = catalog.alphabet("uk") ?: throw AssertionError("no uk alphabet")

    private fun Alphabet.row(ref: String): AlphabetEntry =
        entry(ref) ?: throw AssertionError("no entry \"$ref\" in ${entries.map { it.ref }}")

    private fun rejects(entries: String): String =
        assertFailsWith<CatalogFormatException> { AlphabetFixture.deEntries(entries) }.message.orEmpty()

    // -- registry ----------------------------------------------------------------------

    @Test
    fun filePresenceIsTheRegistry() {
        assertEquals("de", de.language)
        assertEquals(listOf("de", "uk"), catalog.alphabets.keys.toList())
        assertNull(catalog.alphabet("en"))
        assertNull(catalog.alphabet("sw"))
    }

    /** An alphabet is CONTENT: editing one restamps the join, exactly like a word file. */
    @Test
    fun alphabetsFoldIntoTheFingerprint() {
        assertTrue(Fixture.catalog().fingerprint != catalog.fingerprint)
    }

    // -- parsed values -----------------------------------------------------------------

    @Test
    fun aLetterRowParsesFieldForField() {
        val m = de.row("m")
        assertEquals("m", m.glyph)
        assertEquals("M", m.upper)
        assertEquals("em", m.name)
        assertEquals("m", m.ipa)
        assertEquals(AlphabetKind.Letter, m.kind) // the default kind
        assertEquals("mouse", m.exampleSlug)
        assertNull(m.exampleText)
        assertEquals(mapOf("de" to "wie in Maus", "en" to "as in mouse"), m.hints)
        assertEquals(emptyMap(), m.context)
        assertTrue(m.drill) // the default
    }

    @Test
    fun aContextualRowKeepsItsIdAsTheRefAndItsContextByReader() {
        val ich = de.row("ch-ich")
        assertEquals("ch", ich.glyph)
        assertEquals(AlphabetKind.Contextual, ich.kind)
        assertEquals("ç", ich.ipa)
        assertEquals(mapOf("en" to "after e, i"), ich.context)
        assertNull(ich.upper)
        assertNull(ich.name)
        // Three rows share the glyph, so the glyph alone never identifies one.
        assertEquals(listOf("ch-ich", "ch-ach", "ch-chef"), de.entries.filter { it.glyph == "ch" }.map { it.ref })
    }

    /**
     * A rule row is prose the sheet renders: it may carry whitespace no other kind may,
     * and an authored `drill` on it is ignored rather than obeyed — the fixture sets
     * `"drill": true` on exactly this row to pin that.
     */
    @Test
    fun ruleRowsAreProseAndNeverDrillable() {
        val rule = de.row("b d g")
        assertEquals(AlphabetKind.Rule, rule.kind)
        assertEquals(false, rule.drill)
        assertEquals("royal", rule.exampleSlug)
    }

    @Test
    fun drillFalseSurvivesOnARealGrapheme() {
        assertEquals(false, de.row("h-length").drill)
        assertEquals(false, uk.row("ʼ").drill)
    }

    // -- derived tables ----------------------------------------------------------------

    @Test
    fun confusionSetsCloseBothWaysPerAxis() {
        assertEquals(listOf("n"), de.row("m").confusableLook)
        assertEquals(listOf("m"), de.row("n").confusableLook) // never authored
        assertEquals(listOf("ss"), de.row("ß").confusableSound)
        assertEquals(listOf("ß"), de.row("ss").confusableSound)
        assertEquals(listOf("ch-ach", "ch-chef"), de.row("ch-ich").confusableLook)
        assertEquals(listOf("ch-ich"), de.row("ch-chef").confusableLook)
        // The axes stay apart: ch-chef looks like ch-ich but never sounds like it.
        assertEquals(listOf("ch-ach"), de.row("ch-ich").confusableSound)
        assertEquals(emptyList(), de.row("ch-chef").confusableSound)
        assertEquals(listOf("и"), uk.row("і").confusableLook)
    }

    @Test
    fun accessorsReturnTheClosedSets() {
        assertEquals(listOf("ss"), de.lookAlikes("ß").map { it.ref })
        assertEquals(listOf("ß"), de.soundAlikes("ss").map { it.ref })
        assertEquals(emptyList(), de.lookAlikes("nope").map { it.ref })
    }

    /** Groups are the identical IPA string, never an authored table. */
    @Test
    fun homophonesAreDerivedFromIdenticalIpa() {
        assertEquals(listOf("ss"), de.homophones("ß").map { it.ref })
        assertEquals(listOf("ß"), de.homophones("ss").map { it.ref })
        assertEquals(emptyList(), de.homophones("m").map { it.ref })
        assertEquals(emptyList(), uk.homophones("и").map { it.ref }) // ɪ and i differ
        assertEquals(emptyList(), de.homophones("h-length").map { it.ref }) // no ipa at all
    }

    // -- example resolution (§2.2) -----------------------------------------------------

    @Test
    fun theExampleWordComesFromTheAlphabetsOwnLanguage() {
        val example = catalog.alphabetExample(de.row("m"), "de")
        assertEquals(AlphabetExample("mouse", "Maus", "🐭"), example)
        assertEquals("миша", catalog.alphabetExample(uk.row("и"), "uk")?.text)
    }

    /**
     * PROVENANCE: the uk `дж` row carries BOTH an `example` uk never realizes and an
     * `exampleText`. The slug half resolves to null, so the caller falls back to text
     * that names no slug — which is what keeps a recording of `greet` from playing over
     * "джерело" on screen.
     */
    @Test
    fun anUnrealizedExampleSlugDegradesToPlainTextNotToTheSlug() {
        val dzh = uk.row("дж")
        assertEquals("greet", dzh.exampleSlug)
        assertNull(catalog.alphabetExample(dzh, "uk"))
        assertEquals("джерело", dzh.exampleText)
    }

    @Test
    fun theMeaningHalfIsReaderScopedAndNullableWithoutError() {
        assertEquals("mouse", catalog.exampleMeaning("mouse", "en"))
        assertEquals("panya", catalog.exampleMeaning("mouse", "sw"))
        assertNull(catalog.exampleMeaning("greet", "uk")) // uk never realizes it
        assertNull(catalog.exampleMeaning("no-such-slug", "de"))
    }

    // -- gap words ---------------------------------------------------------------------

    @Test
    fun theGapReplacesTheWholeGraphemeWithOneMarker() {
        assertEquals("Wa＿er", gapText("Wasser", "ss"))
        assertEquals("＿ef", gapText("Chef", "ch")) // case-insensitive, first occurrence
        assertEquals("ko＿en", gapText("kochen", "ch"))
    }

    /** The de door realization is authored decomposed; the glyph is composed. */
    @Test
    fun gapMatchingIsNfcInsensitive() {
        val door = catalog.alphabetExample(de.row("ü"), "de")?.text ?: throw AssertionError("no example")
        assertTrue(door != "Tür", "the fixture stopped being decomposed")
        assertEquals("T＿r", gapText(door, "ü"))
    }

    /** Alphabets store U+02BC; a realization keeping the typewriter one still matches. */
    @Test
    fun gapMatchingFoldsTheApostropheClass() {
        val modifier = "\u02bc" // what alphabet files store
        assertEquals("де＿ять", gapText("дев'ять", "в$modifier"))
        assertEquals("де＿ять", gapText("дев\u2019ять", "в$modifier"))
        assertEquals("дев＿ять", gapText("дев'ять", modifier))
        assertEquals(1, glyphOccurrences("дев'ять", modifier))
    }

    /**
     * The occurrence rule §2.2: exactly one, or there is no answerable gap. Lint reports
     * either failure over the real catalog; kern returns null so a content bug costs a
     * pool entry instead of shipping an unanswerable question.
     */
    @Test
    fun onlyASingleOccurrenceYieldsAGapWord() {
        val ch = de.row("ch-ich")
        assertEquals(2, glyphOccurrences("Kochbuch", "ch"))
        assertNull(ch.gapWord("Kochbuch"))
        assertEquals(0, glyphOccurrences("Nacht", "sch"))
        assertNull(de.row("ss").gapWord("Nacht"))
        assertNull(ch.gapWord(null)) // no example resolved at all
        assertEquals("Na＿t", ch.gapWord("Nacht"))
    }

    @Test
    fun everyDrillableGapRowInTheFixtureCanBeGapped() {
        for (entry in de.entries + uk.entries) {
            if (!entry.drill || entry.kind !in setOf(AlphabetKind.Digraph, AlphabetKind.Contextual)) continue
            val alphabet = if (entry in de.entries) de else uk
            val text = catalog.alphabetExample(entry, alphabet.language)?.text ?: entry.exampleText
            assertTrue(entry.gapWord(text) != null, "${alphabet.language} ${entry.ref}: no gap from \"$text\"")
        }
    }

    // -- parse errors ------------------------------------------------------------------

    @Test
    fun unknownKeysAreRejectedIncludingTheDroppedAudioField() {
        val message = rejects("""{ "glyph": "a", "ipa": "a", "audio": "a.mp3" }""")
        assertTrue("alphabet/de.json" in message, message)
        assertTrue("audio" in message, message)
    }

    @Test
    fun shapeErrorsAreRejected() {
        assertTrue("blank \"name\"" in rejects("""{ "glyph": "a", "ipa": "a", "name": " " }"""))
        assertTrue("whitespace" in rejects("""{ "glyph": "a b", "ipa": "a" }"""))
        assertTrue("needs an ipa or a hint" in rejects("""{ "glyph": "a" }"""))
        assertTrue("undeclared language" in rejects("""{ "glyph": "a", "hints": { "es": "…" } }"""))
        assertTrue("expected a boolean" in rejects("""{ "glyph": "a", "ipa": "a", "drill": "false" }"""))
        assertTrue("bad id" in rejects("""{ "glyph": "a", "ipa": "a", "id": "Ch_1" }"""))
        assertTrue("unknown kind" in rejects("""{ "glyph": "a", "ipa": "a", "kind": "vowel" }"""))
        val empty = assertFailsWith<CatalogFormatException> {
            AlphabetFixture.catalogWith("alphabet/de.json", """{ "entries": [] }""")
        }
        assertTrue("empty entries" in empty.message.orEmpty())
    }

    /** Identity: a repeated glyph needs ids, and no two rows may answer to one ref. */
    @Test
    fun duplicateGlyphsWithoutIdsAreRejected() {
        val threeWay = rejects(
            """
            { "glyph": "ch", "id": "ch-ich", "ipa": "ç" },
            { "glyph": "ch", "id": "ch-ach", "ipa": "x" },
            { "glyph": "ch", "ipa": "ʃ" }
            """.trimIndent(),
        )
        assertTrue("authored 3 times" in threeWay, threeWay)
        val duplicateId = rejects(
            """
            { "glyph": "a", "id": "same", "ipa": "a" },
            { "glyph": "b", "id": "same", "ipa": "b" }
            """.trimIndent(),
        )
        assertTrue("duplicate ref \"same\"" in duplicateId, duplicateId)
    }

    @Test
    fun confusableRefsMustNameExactlyOneOtherEntry() {
        assertTrue("ref to itself" in rejects("""{ "glyph": "a", "ipa": "a", "confusable": { "look": ["a"] } }"""))
        assertTrue(
            "matches no entry" in rejects("""{ "glyph": "a", "ipa": "a", "confusable": { "sound": ["q"] } }"""),
        )
        val ambiguous = rejects(
            """
            { "glyph": "a", "ipa": "a", "confusable": { "look": ["ch"] } },
            { "glyph": "ch", "id": "ch-one", "ipa": "ç" },
            { "glyph": "ch", "id": "ch-two", "ipa": "x" }
            """.trimIndent(),
        )
        assertTrue("matches 2 entries" in ambiguous, ambiguous)
        assertTrue("unknown confusable axis" in rejects("""{ "glyph": "a", "ipa": "a", "confusable": { "feel": [] } }"""))
    }
}
