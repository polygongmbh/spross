package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
        variants: List<String> = emptyList(),
    ) = AtlasLanguageEntry(
        code = code,
        tier = tier,
        source = LanguageName(name = source, inForm = "in $source"),
        target = LanguageName(name = target, inForm = "in $target", variants = variants),
    )

    private fun country(
        slug: String,
        tier: Int,
        languages: List<String>,
        source: String,
        target: String,
        sourceVariants: List<String> = emptyList(),
    ) = AtlasCountryEntry(
        slug = slug,
        flag = "🏳",
        tier = tier,
        languages = languages,
        source = CountryName(text = source, variants = sourceVariants,
                             nationality = NationalityName("$source-person")),
        target = CountryName(
            text = target,
            nationality = NationalityName("$target-person", listOf("$target-woman")),
        ),
    )

    /** A de→sw profile: tier 1 is its own, tier 2 the neighbor, tier 3 two rungs out. */
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

    private fun ids(level: Int, kind: CountryTaskKind): Set<String> =
        CountryDrill.tasks(content, level).filter { it.kind == kind }.map { it.id }.toSet()

    /** Tier 1 is derived, not authored: exactly the rows carrying the profile's languages. */
    @Test
    fun rungOneAsksOnlyTheProfilesOwnCountriesByName() {
        val tasks = CountryDrill.tasks(content, 1)
        assertEquals(setOf(CountryTaskKind.CountryName), tasks.map { it.kind }.toSet())
        assertEquals(home, tasks.map { it.id }.toSet())
    }

    /**
     * The ladder's whole shape, rung by rung: what each one may ask and how far out it
     * reaches. ONE of the two columns moves per step and never both — a rung that brought a
     * question AND a tier would leave the learner unable to say what got harder.
     */
    @Test
    fun eachRungBringsExactlyOneNewThing() {
        val expected = listOf(
            1 to setOf(CountryTaskKind.CountryName),
            1 to setOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName),
            1 to setOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName,
                       CountryTaskKind.Nationality),
            2 to setOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName,
                       CountryTaskKind.Nationality),
            2 to setOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName,
                       CountryTaskKind.Nationality, CountryTaskKind.SpokenIn),
            3 to setOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName,
                       CountryTaskKind.Nationality, CountryTaskKind.SpokenIn),
            3 to setOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName,
                       CountryTaskKind.Nationality, CountryTaskKind.SpokenIn,
                       CountryTaskKind.FlagCountry),
            4 to setOf(CountryTaskKind.CountryName, CountryTaskKind.LanguageName,
                       CountryTaskKind.Nationality, CountryTaskKind.SpokenIn,
                       CountryTaskKind.FlagCountry),
            4 to CountryTaskKind.entries.toSet(),
        )
        assertEquals(9, CountryDrill.MAX_LEVEL)
        assertEquals(expected.size, CountryDrill.MAX_LEVEL)
        for ((index, want) in expected.withIndex()) {
            val level = index + 1
            assertEquals(want.first, CountryDrill.tierCeiling(level), "rung $level reaches wrong")
            assertEquals(want.second, CountryDrill.kinds(level).toSet(), "rung $level asks wrong")
        }
        for ((below, above) in expected.zipWithNext()) {
            assertTrue(
                (below.first == above.first) != (below.second == above.second),
                "a rung moved both the pool and the questions, or neither",
            )
        }
    }

    @Test
    fun rungTwoAddsTheLanguagesAndRungThreeThePeople() {
        assertEquals(home, ids(2, CountryTaskKind.CountryName))
        assertEquals(setOf("de", "sw"), ids(2, CountryTaskKind.LanguageName))
        assertTrue(CountryDrill.tasks(content, 2).none { it.kind == CountryTaskKind.Nationality })
        assertEquals(home, ids(3, CountryTaskKind.Nationality))
        assertTrue(CountryDrill.tasks(content, 3).none { it.kind == CountryTaskKind.SpokenIn })
    }

    @Test
    fun everyRungKeepsEverythingBelowIt() {
        val pools = (1..CountryDrill.MAX_LEVEL).map { level ->
            CountryDrill.tasks(content, level).map { it.kind to it.id }.toSet()
        }
        for ((below, above) in pools.zipWithNext()) {
            assertTrue(below.all { it in above }, "a rung dropped what the one below it opened")
        }
        assertEquals(home + setOf("neighbor", "far"), ids(9, CountryTaskKind.CountryName))
        assertEquals(setOf("de", "sw", "es", "fr"), ids(9, CountryTaskKind.SpokenWhere))
    }

    @Test
    fun spokenWhereArrivesOnlyOnTheTopRung() {
        for (level in 1 until CountryDrill.MAX_LEVEL) {
            assertTrue(
                CountryDrill.tasks(content, level).none { it.kind == CountryTaskKind.SpokenWhere },
                "rung $level asks where a language is spoken",
            )
        }
    }

    /**
     * A ceiling the content does not reach is not an empty rung: the pool widens until
     * something stands, which is what lets tiers 3 and 4 land as pure content later.
     */
    @Test
    fun aLadderTallerThanTheContentRepeatsThePoolBelow() {
        val shallow = content.copy(
            countries = content.countries.filter { it.tier <= 1 },
            languages = content.languages.filter { it.tier <= 1 },
        )
        // Rungs 3 and 4 ask the same kinds, so what is left between them is the tier —
        // and with nothing authored out there, the pool below is what both stand on.
        assertEquals(
            CountryDrill.tasks(shallow, 3).map { it.kind to it.id },
            CountryDrill.tasks(shallow, 4).map { it.kind to it.id },
        )
    }

    /** A gap at the BOTTOM is skipped over, never asked as a rung with no question in it. */
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
                        sourceVariants = listOf("Schweiz")),
            ),
        )
        assertEquals(
            home,
            CountryDrill.tasks(twinned, 1).map { it.id }.toSet(),
            "a name the two languages share was asked anyway",
        )
    }

    /**
     * The flag question is the outer rungs' own, and it takes the WHOLE pool: a card with no
     * name on it gives nothing away, so the countries the two languages agree on come back.
     */
    @Test
    fun theFlagQuestionArrivesOnRungSevenAndAsksEveryCountry() {
        val twinned = content.copy(
            countries = content.countries + country("same", 1, listOf("de"), "Malta", "Malta"),
        )
        for (level in 1..6) {
            assertTrue(
                CountryDrill.tasks(twinned, level).none { it.kind == CountryTaskKind.FlagCountry },
                "rung $level shows a bare flag",
            )
        }
        val flags = CountryDrill.tasks(twinned, 7).filter { it.kind == CountryTaskKind.FlagCountry }
        assertContains(flags.map { it.id }, "same")
        val task = assertNotNull(flags.firstOrNull { it.id == "homeland" })
        assertEquals(null, task.promptText, "the flag question wrote a name on the card")
        assertEquals("🏳", task.promptEmoji)
        assertEquals("Ujerumani", task.display)
        assertEquals("Deutschland", task.gloss, "the reveal names the country on the asking side")
    }

    /** A pair that agrees on EVERY name still owes rung 1 a question, easy or not. */
    @Test
    fun aPairThatAgreesOnEveryNameKeepsItsRung() {
        val twins = content.copy(
            countries = listOf(country("same", 1, listOf("de"), "Malta", "Malta")),
        )
        assertEquals(listOf("same"), CountryDrill.tasks(twins, 1).map { it.id })
    }

    /** Every language of a country answers it, including ones the rung has not opened. */
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
        assertEquals("Kijerumani", task.display, "the reveal shows a language the rung has opened")
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
        for (level in 1..CountryDrill.MAX_LEVEL) {
            for (task in CountryDrill.tasks(content, level, reverse = true)) {
                if (task.kind == CountryTaskKind.LanguageName ||
                    task.kind == CountryTaskKind.SpokenWhere
                ) {
                    // A language has no flag to hold back in the first place.
                    assertEquals(null, task.promptEmoji, "rung $level: a language flew a flag")
                    assertTrue(!task.emojiIsGiveaway, "rung $level: nothing to give away")
                    continue
                }
                assertEquals("🏳", task.promptEmoji, "rung $level dropped a reversed ${task.kind}'s flag")
                assertTrue(task.emojiIsGiveaway, "rung $level: reversed ${task.kind} may show its flag")
            }
        }
    }

    /** Forward, the flag is no giveaway — it is context, and shown from the first frame. */
    @Test
    fun aForwardQuestionShowsItsFlagOutright() {
        for (level in 1..CountryDrill.MAX_LEVEL) {
            for (task in CountryDrill.tasks(content, level)) {
                assertTrue(!task.emojiIsGiveaway, "rung $level withheld a forward ${task.kind}'s flag")
            }
        }
        assertTrue(CountryDrill.tasks(content, 1).all { it.promptEmoji != null })
        // The flag question's own flag is the QUESTION — never withheld, in any direction.
        val flags = CountryDrill.tasks(content, 7).filter { it.kind == CountryTaskKind.FlagCountry }
        assertTrue(flags.isNotEmpty())
        assertTrue(flags.none { it.emojiIsGiveaway }, "the flag question hid its own flag")
    }

    /** The flag KIND is not merely emptied in reverse — it is never built there. */
    @Test
    fun theFlagQuestionDoesNotExistInReverse() {
        for (level in 1..CountryDrill.MAX_LEVEL) {
            assertTrue(
                CountryDrill.kinds(level, reverse = true).none { it == CountryTaskKind.FlagCountry },
                "rung $level lists the flag question in reverse",
            )
            assertTrue(
                CountryDrill.tasks(content, level, reverse = true)
                    .none { it.kind == CountryTaskKind.FlagCountry },
                "rung $level built a flag question in reverse",
            )
        }
    }

    /**
     * Rung 7's whole novelty is the flag, which reverse does not have — so there it is a
     * rung that adds nothing and legally stands on the pool below, exactly as an unauthored
     * tier does. The ladder stays climbable; it just has one flat step in that direction.
     */
    @Test
    fun theFlagRungRepeatsThePoolBelowItInReverse() {
        assertEquals(
            CountryDrill.tasks(content, 6, reverse = true).map { it.kind to it.id },
            CountryDrill.tasks(content, 7, reverse = true).map { it.kind to it.id },
        )
        assertTrue(CountryDrill.tasks(content, 7, reverse = true).isNotEmpty())
    }

    /**
     * Fast is earned by having STOOD on the top rung, not by standing there now: the app
     * keeps the highest rung any run reached, and that is what buys the modifier.
     */
    @Test
    fun fastIsOfferedOnlyOnceTheTopRungHasBeenReached() {
        for (best in 0 until CountryDrill.MAX_LEVEL) {
            assertTrue(!CountryDrill.fastUnlocked(best), "rung $best bought fast mode")
        }
        assertTrue(CountryDrill.fastUnlocked(CountryDrill.MAX_LEVEL))
        assertTrue(CountryDrill.fastUnlocked(CountryDrill.MAX_LEVEL + 3), "a stored best above the top")
    }

    /** Three clean wins a rung, or one where fast was earned. */
    @Test
    fun aRungCostsThreeCleanWinsAndFastSpendsOne() {
        assertEquals(3, CountryDrill.winsToAdvance(fast = false))
        assertEquals(1, CountryDrill.winsToAdvance(fast = true))

        var step = DrillRamp.RungStep(1, 0)
        repeat(2) { step = CountryDrill.step(step.level, step.winsAtLevel, correct = true, clean = true) }
        assertEquals(1, step.level, "two wins moved a three-win rung")
        step = CountryDrill.step(step.level, step.winsAtLevel, correct = true, clean = true)
        assertEquals(2, step.level)

        val quick = CountryDrill.step(1, 0, correct = true, clean = true, fast = true)
        assertEquals(2, quick.level, "fast still asked for more than one win")
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
    fun theReferenceTableGroupsByTierInnermostFirst() {
        val groups = CountryDrill.reference(content)
        assertEquals(listOf(1, 2, 3), groups.map { it.tier })
        val row = groups.first().rows.first()
        assertEquals("Deutschland", row.source)
        assertEquals("Ujerumani", row.target)
        assertEquals("Ujerumani-person", row.targetNationality)
        assertEquals(listOf("Deutsch"), row.sourceLanguages)
        assertEquals(listOf("Kijerumani"), row.targetLanguages)
    }

    /** The named rungs cap the CONTENT; the number climbs on so a climbed-out atlas still counts. */
    @Test
    fun theRungKeepsCountingPastTheLaddersTop() {
        var step = DrillRamp.RungStep(CountryDrill.MAX_LEVEL, 0)
        repeat(4) { step = CountryDrill.step(step.level, step.winsAtLevel, correct = true, clean = true) }
        assertEquals(CountryDrill.MAX_LEVEL + 1, step.level, "three clean wins carried it past the top")
        assertEquals(
            CountryDrill.kinds(CountryDrill.MAX_LEVEL),
            CountryDrill.kinds(step.level),
            "and asks the top rung's questions up there",
        )
        assertEquals(1, CountryDrill.step(1, 0, correct = false, clean = true).level)
    }
}
