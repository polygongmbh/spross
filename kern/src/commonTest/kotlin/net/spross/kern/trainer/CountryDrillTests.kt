package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.AtlasCountryEntry
import net.spross.kern.catalog.AtlasLanguageEntry
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.CountryName
import net.spross.kern.catalog.LanguageName
import net.spross.kern.catalog.NationalityName

/**
 * The ladder and the task shapes, on a hand-built atlas: three tiers, a country speaking
 * two languages, and a language spoken in two countries — the situations the real catalog
 * will keep reshuffling while these rules stay put.
 */
class CountryDrillTests {

    private fun language(
        code: String,
        tier: Int,
        source: String,
        target: String,
        accepts: List<String> = emptyList(),
    ) = AtlasLanguageEntry(
        code = code,
        tier = tier,
        source = LanguageName(name = source, inForm = "in $source"),
        target = LanguageName(name = target, inForm = "in $target", accepts = accepts),
    )

    private fun country(
        slug: String,
        tier: Int,
        languages: List<String>,
        source: String,
        target: String,
        sourceAccepts: List<String> = emptyList(),
    ) = AtlasCountryEntry(
        slug = slug,
        flag = "🏳",
        tier = tier,
        languages = languages,
        source = CountryName(text = source, accepts = sourceAccepts,
                             nationality = NationalityName("$source-person")),
        target = CountryName(
            text = target,
            nationality = NationalityName("$target-person", listOf("$target-woman")),
        ),
    )

    /** A de→sw profile: tier 1 is its own, tier 2 the neighbor, tier 3 two Sprossen out. */
    private val content = CountryDrillContent(
        source = "de",
        target = "sw",
        countries = listOf(
            country("homeland", 1, listOf("de"), "Deutschland", "Ujerumani"),
            country("second-home", 1, listOf("de", "fr"), "Österreich", "Austria"),
            country("islands", 1, listOf("sw"), "Kenia", "Kenya"),
            country("neighbor", 2, listOf("es"), "Spanien", "Uhispania"),
            country("far", 3, listOf("fr"), "Frankreich", "Ufaransa"),
        ),
        languages = listOf(
            language("de", 1, "Deutsch", "Kijerumani"),
            language("sw", 1, "Suaheli", "Kiswahili", listOf("Kisuaheli")),
            language("es", 2, "Spanisch", "Kihispania"),
            language("fr", 3, "Französisch", "Kifaransa"),
        ),
    )

    private val home = setOf("homeland", "second-home", "islands")

    private fun ids(sprosse: Int, kind: CountryTaskKind): Set<String> =
        CountryDrill.tasks(content, sprosse).filter { it.kind == kind }.map { it.id }.toSet()

    /** Tier 1 is derived, not authored: exactly the rows carrying the profile's languages. */
    @Test
    fun sprosseOneAsksOnlyTheProfilesOwnCountriesByName() {
        val tasks = CountryDrill.tasks(content, 1)
        assertEquals(setOf(CountryTaskKind.CountryName), tasks.map { it.kind }.toSet())
        assertEquals(home, tasks.map { it.id }.toSet())
    }

    /**
     * ONE of the two columns moves per step and never both — a Sprosse that brought a
     * question AND a tier would leave the learner unable to say what got harder.
     */
    @Test
    fun eachSprosseBringsExactlyOneNewThing() {
        for (sprosse in 2..CountryDrill.MAX_SPROSSE) {
            val widens = CountryDrill.tierCeiling(sprosse) != CountryDrill.tierCeiling(sprosse - 1)
            val asksMore = CountryDrill.kinds(sprosse) != CountryDrill.kinds(sprosse - 1)
            assertTrue(widens != asksMore, "Sprosse $sprosse moved both the pool and the questions, or neither")
        }
    }

    /**
     * Arriving at a Sprosse, the draw leads with what that Sprosse ADDED — a question the one
     * below could not ask. The atlas widens on two axes at once, so what it added is read as
     * the difference of the two pools rather than off the kind or the tier alone.
     */
    @Test
    fun aSprosseJustReachedAsksWhatItAdded() {
        for (sprosse in 2..CountryDrill.MAX_SPROSSE) {
            val below = CountryDrill.tasks(content, sprosse - 1).mapTo(mutableSetOf()) { DrillSolved.key(it) }
            // A Sprosse whose widening this atlas has no content for adds nothing in FACT
            // however the ladder reads, and falls back to the whole pool rather than to nothing.
            if (CountryDrill.tasks(content, sprosse).all { DrillSolved.key(it) in below }) continue
            for (seed in 1..8) {
                val task = assertNotNull(
                    CountryDrill.sample(content, sprosse, false, null, emptySet(), Random(seed), arriving = true),
                )
                assertFalse(
                    DrillSolved.key(task) in below,
                    "Sprosse $sprosse arrived on a question Sprosse ${sprosse - 1} could already ask",
                )
            }
        }
    }

    @Test
    fun everySprosseKeepsEverythingBelowIt() {
        val pools = (1..CountryDrill.MAX_SPROSSE).map { sprosse ->
            CountryDrill.tasks(content, sprosse).map { it.kind to it.id }.toSet()
        }
        for ((below, above) in pools.zipWithNext()) {
            assertTrue(below.all { it in above }, "a Sprosse dropped what the one below it opened")
        }
        assertEquals(home + setOf("neighbor", "far"), ids(CountryDrill.MAX_SPROSSE, CountryTaskKind.CountryName))
    }

    /**
     * A ceiling the content does not reach is not an empty Sprosse: the pool widens until
     * something stands, which is what lets tiers 3 and 4 land as pure content later.
     */
    @Test
    fun aLadderTallerThanTheContentRepeatsThePoolBelow() {
        val shallow = content.copy(
            countries = content.countries.filter { it.tier <= 1 },
            languages = content.languages.filter { it.tier <= 1 },
        )
        // Sprossen 3 and 4 ask the same kinds, so what is left between them is the tier —
        // and with nothing authored out there, the pool below is what both stand on.
        assertEquals(
            CountryDrill.tasks(shallow, 3).map { it.kind to it.id },
            CountryDrill.tasks(shallow, 4).map { it.kind to it.id },
        )
    }

    /** A gap at the BOTTOM is skipped over, never asked as a Sprosse with no question in it. */
    @Test
    fun aTierWithNothingInItIsSkippedRatherThanAsked() {
        val gapped = content.copy(countries = content.countries.filter { it.tier != 1 })
        assertEquals(setOf("neighbor"), CountryDrill.tasks(gapped, 1).map { it.id }.toSet())
    }

    /**
     * A name both languages spell alike is no question: the prompt would be the answer.
     * Accents do not make two names ("Peru"/"Perú"), a different spelling does
     * ("Kenia"/"Kenya"), and a form the other side accepts counts as the same name.
     */
    @Test
    fun aCountryBothLanguagesCallTheSameIsNotAskedByName() {
        val twinned = content.copy(
            countries = content.countries + listOf(
                country("same", 1, listOf("de"), "Malta", "Malta"),
                country("accented", 1, listOf("de"), "Peru", "Perú"),
                country("variant-match", 1, listOf("de"), "die Schweiz", "Schweiz",
                        sourceAccepts = listOf("Schweiz")),
            ),
        )
        assertEquals(
            home,
            CountryDrill.tasks(twinned, 1).map { it.id }.toSet(),
            "a name the two languages share was asked anyway",
        )
    }

    /** The first Sprosse that asks a bare flag. */
    private val flagSprosse = (1..CountryDrill.MAX_SPROSSE).first { CountryTaskKind.FlagCountry in CountryDrill.kinds(it) }

    /**
     * The flag question takes the WHOLE pool: a card with no name on it gives nothing away,
     * so the countries the two languages agree on come back.
     */
    @Test
    fun theFlagQuestionAsksEveryCountry() {
        val twinned = content.copy(
            countries = content.countries + country("same", 1, listOf("de"), "Malta", "Malta"),
        )
        val flags = CountryDrill.tasks(twinned, flagSprosse).filter { it.kind == CountryTaskKind.FlagCountry }
        assertContains(flags.map { it.id }, "same")
        val task = assertNotNull(flags.firstOrNull { it.id == "homeland" })
        assertEquals(null, task.promptText, "the flag question wrote a name on the card")
        assertEquals("Ujerumani", task.display)
        assertEquals("Deutschland", task.gloss, "the reveal names the country on the asking side")
    }

    /** A pair that agrees on EVERY name still owes Sprosse 1 a question, easy or not. */
    @Test
    fun aPairThatAgreesOnEveryNameKeepsItsSprosse() {
        val twins = content.copy(
            countries = listOf(country("same", 1, listOf("de"), "Malta", "Malta")),
        )
        assertEquals(listOf("same"), CountryDrill.tasks(twins, 1).map { it.id })
    }

    /** Every language of a country answers it, including ones the Sprosse has not opened. */
    @Test
    fun spokenInAcceptsEveryLanguageTheCountryCarries() {
        val task = assertNotNull(
            CountryDrill.tasks(content, 5).firstOrNull {
                it.kind == CountryTaskKind.SpokenIn && it.id == "second-home"
            },
        )
        assertEquals("Österreich", task.promptText)
        assertContains(task.accepted, "Kijerumani")
        assertContains(task.accepted, "Kifaransa")
        assertEquals("Kijerumani", task.display, "the reveal shows a language the Sprosse has opened")
        assertEquals("Austria", task.gloss)
    }

    @Test
    fun spokenWhereAcceptsEveryCountryTheLanguageReaches() {
        val task = assertNotNull(
            CountryDrill.tasks(content, 9).firstOrNull {
                it.kind == CountryTaskKind.SpokenWhere && it.id == "de"
            },
        )
        assertEquals("Deutsch", task.promptText)
        assertContains(task.accepted, "Ujerumani")
        assertContains(task.accepted, "Austria")
        assertEquals("Kijerumani", task.gloss)
    }

    @Test
    fun theNationalityKindAsksThePersonAndRevealsTheCountry() {
        val task = assertNotNull(
            CountryDrill.tasks(content, 3).firstOrNull {
                it.kind == CountryTaskKind.Nationality && it.id == "homeland"
            },
        )
        assertEquals("Deutschland-person", task.promptText)
        assertEquals("Ujerumani-person", task.display)
        assertContains(task.accepted, "Ujerumani-woman")
        assertEquals("Ujerumani", task.gloss)
    }

    /** Reverse is a direction, not another ladder: the same pool, asked the other way. */
    @Test
    fun reverseSwapsThePromptAndTheAcceptedSide() {
        val forward = CountryDrill.tasks(content, 1).first { it.id == "homeland" }
        val back = CountryDrill.tasks(content, 1, reverse = true).first { it.id == "homeland" }
        assertEquals("Deutschland", forward.promptText)
        assertEquals("Ujerumani", forward.display)
        assertEquals("Ujerumani", back.promptText)
        assertEquals("Deutschland", back.display)
        assertEquals(
            CountryDrill.tasks(content, 6).map { it.kind to it.id },
            CountryDrill.tasks(content, 6, reverse = true).map { it.kind to it.id },
        )
    }

    /**
     * A reversed run answers in the learner's OWN language, so showing the flag while the
     * answer is owed would hand it over. The task still CARRIES it — a picture the learner
     * never gets to see is one the task might as well not have had — and says so with
     * [CountryDrillTask.emojiIsGiveaway], which the card obeys by holding it to the reveal.
     */
    @Test
    fun aReversedCountryQuestionKeepsItsFlagAndCallsItAGiveaway() {
        for (sprosse in 1..CountryDrill.MAX_SPROSSE) {
            for (task in CountryDrill.tasks(content, sprosse, reverse = true)) {
                if (task.kind == CountryTaskKind.LanguageName ||
                    task.kind == CountryTaskKind.SpokenWhere
                ) {
                    // A language has no flag to hold back in the first place.
                    assertEquals(null, task.promptEmoji, "Sprosse $sprosse: a language flew a flag")
                    assertTrue(!task.emojiIsGiveaway, "Sprosse $sprosse: nothing to give away")
                    continue
                }
                assertEquals("🏳", task.promptEmoji, "Sprosse $sprosse dropped a reversed ${task.kind}'s flag")
                assertTrue(task.emojiIsGiveaway, "Sprosse $sprosse: reversed ${task.kind} may show its flag")
            }
        }
    }

    /** Forward, the flag is no giveaway — it is context, and shown from the first frame. */
    @Test
    fun aForwardQuestionShowsItsFlagOutright() {
        for (sprosse in 1..CountryDrill.MAX_SPROSSE) {
            for (task in CountryDrill.tasks(content, sprosse)) {
                assertTrue(!task.emojiIsGiveaway, "Sprosse $sprosse withheld a forward ${task.kind}'s flag")
            }
        }
        assertTrue(CountryDrill.tasks(content, 1).all { it.promptEmoji != null })
        // The flag question's own flag is the QUESTION — never withheld, in any direction.
        val flags = CountryDrill.tasks(content, flagSprosse).filter { it.kind == CountryTaskKind.FlagCountry }
        assertTrue(flags.isNotEmpty())
        assertTrue(flags.none { it.emojiIsGiveaway }, "the flag question hid its own flag")
    }

    /** The flag KIND is not merely emptied in reverse — it is never built there. */
    @Test
    fun theFlagQuestionDoesNotExistInReverse() {
        for (sprosse in 1..CountryDrill.MAX_SPROSSE) {
            assertTrue(
                CountryDrill.tasks(content, sprosse, reverse = true)
                    .none { it.kind == CountryTaskKind.FlagCountry },
                "Sprosse $sprosse built a flag question in reverse",
            )
        }
    }

    /**
     * The flag Sprosse's whole novelty is the flag, which reverse does not have — so there it
     * is a Sprosse that adds nothing and legally stands on the pool below, exactly as an
     * unauthored tier does, and the only one that says so.
     */
    @Test
    fun theFlagSprosseRepeatsThePoolBelowItInReverse() {
        assertEquals(
            CountryDrill.tasks(content, flagSprosse - 1, reverse = true).map { it.kind to it.id },
            CountryDrill.tasks(content, flagSprosse, reverse = true).map { it.kind to it.id },
        )
        assertEquals(
            listOf(flagSprosse),
            (1..CountryDrill.MAX_SPROSSE).filter { CountryDrill.repeatsBelow(it, reverse = true) },
        )
        assertTrue((1..CountryDrill.MAX_SPROSSE).none { CountryDrill.repeatsBelow(it, reverse = false) })
    }

    /**
     * Fast is earned by having STOOD on the top Sprosse, not by standing there now: the app
     * keeps the highest Sprosse any run reached, and that is what buys the modifier.
     */
    @Test
    fun fastIsOfferedOnlyOnceTheTopSprosseHasBeenReached() {
        assertFalse(CountryDrill.fastUnlocked(CountryDrill.MAX_SPROSSE / 2))
        assertTrue(CountryDrill.fastUnlocked(CountryDrill.MAX_SPROSSE))
        assertTrue(CountryDrill.fastUnlocked(CountryDrill.MAX_SPROSSE + 3), "a stored best above the top")
    }

    @Test
    fun theSameSeedDrawsTheSameRun() {
        fun run(): List<String> {
            val rng = Random(7)
            return (1..20).map { assertNotNull(CountryDrill.sample(content, 4, false, null, emptySet(), rng)).id }
        }
        assertEquals(run(), run())
        assertTrue(run().toSet().size > 1, "the run asked one question twenty times")
    }

    /** One resample, not a loop: the repeat needs two unlucky draws to survive. */
    @Test
    fun theLastAnswerIsResampledOnce() {
        fun hits(avoid: String?) = (1..400).count {
            CountryDrill.sample(content, 1, false, avoid, emptySet(), Random(it.toLong()))?.id == "homeland"
        }
        assertTrue(hits("homeland") < hits(null), "avoidId bought nothing")
    }

    @Test
    fun theReferenceTableShowsBothSidesOfEveryTier() {
        val groups = CountryDrill.reference(content)
        assertEquals(setOf(1, 2, 3), groups.map { it.tier }.toSet())
        val row = groups.first().rows.first()
        assertEquals("Deutschland", row.source)
        assertEquals("Ujerumani", row.target)
        assertEquals("Ujerumani-person", row.targetNationality)
        assertEquals(listOf("Deutsch"), row.sourceLanguages)
        assertEquals(listOf("Kijerumani"), row.targetLanguages)
    }

    /** The named Sprossen cap the CONTENT; the number climbs on so a climbed-out atlas still counts. */
    @Test
    fun theSprosseKeepsCountingPastTheLaddersTop() {
        val step = CountryDrill.step(CountryDrill.MAX_SPROSSE, CountryDrill.WINS_TO_ADVANCE - 1, correct = true, clean = true)
        assertEquals(CountryDrill.MAX_SPROSSE + 1, step.sprosse)
        assertEquals(
            CountryDrill.kinds(CountryDrill.MAX_SPROSSE),
            CountryDrill.kinds(step.sprosse),
            "and asks the top Sprosse's questions up there",
        )
    }

}
