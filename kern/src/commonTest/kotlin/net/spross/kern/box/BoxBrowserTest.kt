package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.MapCatalogSource
import net.spross.kern.design.Palette
import net.spross.kern.model.CardPhase

/**
 * Browsing the box: which shelves are listed, which one opens, what a pack would take in,
 * and what a single row has to state about its card.
 */
class BoxBrowserTest {
    private val now = Box.day1
    private val future = Box.plusDays(now, 5.0)

    /**
     * Three groups over four areas: `home` titled for the reader, `work` in English only,
     * `wild` titled in no language at all — the three rungs of the heading fallback.
     * The concepts are empty because the browser reads the BOX's cards, never the catalog's.
     */
    private val catalog: Catalog = Catalog.load(
        MapCatalogSource(
            mapOf(
                "areas.json" to """
                    [
                     { "group": "home", "titles": { "de": "Zuhause", "en": "Home" },
                       "areas": [{ "area": "kitchen", "emoji": "🍳" }, { "area": "bath", "emoji": "🛁" }] },
                     { "group": "work", "titles": { "en": "Work" },
                       "areas": [{ "area": "office", "emoji": "🏢" }] },
                     { "group": "wild",
                       "areas": [{ "area": "forest", "emoji": "🌲" }] }
                    ]
                """.trimIndent(),
                "languages.json" to """
                    {
                     "de": { "name": "Deutsch", "englishName": "German", "flag": "🇩🇪" },
                     "en": { "name": "English", "englishName": "English", "flag": "🇬🇧" },
                     "sw": { "name": "Kiswahili", "englishName": "Swahili", "flag": "🇹🇿" }
                    }
                """.trimIndent(),
                "areas/kitchen/concepts.json" to "[]",
                "areas/bath/concepts.json" to "[]",
                "areas/office/concepts.json" to "[]",
                "areas/forest/concepts.json" to "[]",
            ),
        ),
    )

    private fun stats(state: BoxState): BoxStatistics = BoxEngine.statistics(state, now, Box.TZ)

    private fun sections(state: BoxState, source: String = "de"): List<AreaGroupSection> =
        BoxBrowser.sections(catalog, stats(state), source)

    @Test
    fun shelvesFollowTheManifestAndAnEmptyOneDropsOut() {
        val state = Box.state(
            listOf(
                Box.word(1, area = "office"),
                Box.word(2, area = "kitchen"),
                Box.word(3, area = "forest"),
            ),
        )

        // Manifest order, not the order cards arrived in; `bath` holds nothing, so `home`
        // lists only the kitchen.
        assertEquals(
            listOf(
                AreaGroupSection("home", "Zuhause", listOf("kitchen")),
                AreaGroupSection("work", "Work", listOf("office")),
                AreaGroupSection("wild", "Wild", listOf("forest")),
            ),
            sections(state),
        )
    }

    /** A group the manifest never titled still names its shelf — with the id, visibly wrong. */
    @Test
    fun headingsFallBackThroughEnglishToTheGroupId() {
        val state = Box.state(
            listOf(Box.word(1, area = "kitchen"), Box.word(2, area = "office"), Box.word(3, area = "forest")),
        )

        assertEquals(listOf("Home", "Work", "Wild"), sections(state, source = "sw").map { it.title })
    }

    @Test
    fun ownWordsListLastAndBelongToNoGroup() {
        val catalogOnly = Box.state(listOf(Box.word(1, area = "forest"), Box.word(2, area = "kitchen")))
        assertEquals(listOf("kitchen", "forest"), BoxBrowser.areaNames(catalog, stats(catalogOnly)))

        val withOwn = Box.state(
            listOf(
                Box.word(1, area = "forest"),
                Box.word(2, area = "kitchen"),
                Box.word(3, area = OwnWords.AREA),
            ),
        )
        assertEquals(
            listOf("kitchen", "forest", OwnWords.AREA),
            BoxBrowser.areaNames(catalog, stats(withOwn)),
        )
        assertTrue(sections(withOwn).none { OwnWords.AREA in it.areas })
    }

    @Test
    fun theBrowserOpensWhereTheLearnerLeftOff() {
        var state = Box.state(listOf(Box.word(1, area = "kitchen"), Box.word(2, area = "office")))
        // Nothing started anywhere: the first shelf opens, so the screen is never fully folded.
        assertEquals("home", BoxBrowser.defaultExpandedGroupId(sections(state), stats(state)))

        state = Box.inject(state, Box.sched("w02", dueMillis = future, lastReviewMillis = now))
        assertEquals("work", BoxBrowser.defaultExpandedGroupId(sections(state), stats(state)))
    }

    /** A sleeping word is not work in progress — it must not decide where the browser opens. */
    @Test
    fun aSuspendedShelfDoesNotCountAsStarted() {
        var state = Box.state(listOf(Box.word(1, area = "kitchen"), Box.word(2, area = "office")))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = future, lastReviewMillis = now, suspended = true),
        )
        state = Box.inject(state, Box.sched("w02", dueMillis = future, lastReviewMillis = now))

        assertEquals("work", BoxBrowser.defaultExpandedGroupId(sections(state), stats(state)))
    }

    @Test
    fun noSectionsMeansNothingToOpen() {
        val state = Box.state(emptyList())

        assertNull(BoxBrowser.defaultExpandedGroupId(sections(state), stats(state)))
    }

    @Test
    fun aShelfListsItsCardsInSeedOrder() {
        val state = Box.state(
            listOf(
                Box.word(3, area = "kitchen"),
                Box.word(1, area = "kitchen"),
                Box.word(2, area = "office"),
            ),
        )

        assertEquals(listOf("w01", "w03"), BoxBrowser.cardsInArea(state, "kitchen").map { it.id })
    }

    @Test
    fun packingCountsOnlyWhatTheEngineWouldTakeIn() {
        var state = Box.state((1..3).map { Box.word(it, area = "kitchen") } + Box.word(4, area = "office"))
        state = Box.inject(state, Box.sched("w01", dueMillis = future, lastReviewMillis = now))
        state = BoxEngine.enqueue(state, listOf("w03"))

        // w01 is already scheduled, w03 already queued — only w02 is left to add.
        assertEquals(listOf("w02"), BoxBrowser.enqueueableCardIds(state, "kitchen"))
        assertEquals(1, BoxBrowser.enqueueableCount(state, "kitchen"))

        // The count and the pack read the same predicate, so packing the shelf empties it.
        val packed = BoxEngine.enqueue(state, BoxBrowser.enqueueableCardIds(state, "kitchen"))
        assertEquals(0, BoxBrowser.enqueueableCount(packed, "kitchen"))
        assertEquals(1, BoxBrowser.enqueueableCount(packed, "office"))
    }

    @Test
    fun aSleepingCardOffersWakingWhateverTheRowStandsIn() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = future, lastReviewMillis = now, suspended = true),
        )

        assertEquals(CardRowState.Sleeping, BoxBrowser.cardRowState(state, "w01", packOffered = false))
        assertEquals(CardRowState.Sleeping, BoxBrowser.cardRowState(state, "w01", packOffered = true))
    }

    /**
     * A row packs or unpacks itself, alone, only in a search result — where the learner
     * reached this one card by typing its name rather than browsing a shelf
     * ([packOffered] true). A shelf listing packs and unpacks its own queue in a batch
     * instead. A word already queued always states so, but
     * [CardRowState.Packed.removalOffered] follows [packOffered] the same way
     * [CardRowState.PackOffered] does.
     */
    @Test
    fun theOfferToPackAndUnpackIsPerWordOnlyWhereWordsArePackedOneAtATime() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = BoxEngine.enqueue(state, listOf("w02"))

        assertEquals(CardRowState.PackOffered, BoxBrowser.cardRowState(state, "w01", packOffered = true))
        assertEquals(
            CardRowState.Packed(removalOffered = true),
            BoxBrowser.cardRowState(state, "w02", packOffered = true),
        )
        // No per-word offer, and new is silence: an unqueued card states nothing.
        assertEquals(CardRowState.Plain, BoxBrowser.cardRowState(state, "w01", packOffered = false))
        // A queued one still says so, but the area listing's shelf packs and unpacks
        // in a batch — the row itself offers nothing.
        assertEquals(
            CardRowState.Packed(removalOffered = false),
            BoxBrowser.cardRowState(state, "w02", packOffered = false),
        )
    }

    @Test
    fun dequeueableCardsAreTheAreasQueuedCards() {
        var state = Box.state((1..3).map { Box.word(it, area = "kitchen") } + Box.word(4, area = "office"))
        state = BoxEngine.enqueue(state, listOf("w01", "w03", "w04"))

        assertEquals(listOf("w01", "w03"), BoxBrowser.dequeueableCardIds(state, "kitchen"))
        assertEquals(2, BoxBrowser.dequeueableCount(state, "kitchen"))
        assertEquals(listOf("w04"), BoxBrowser.dequeueableCardIds(state, "office"))
    }

    /** Every shelf at once lists what each shelf lists on its own. */
    @Test
    fun theGroupedShelvesMatchEachShelfsOwnListing() {
        val state = Box.state(
            (1..3).map { Box.word(it, area = "kitchen") } + Box.word(4, area = "office"),
        )
        val grouped = BoxBrowser.cardsByArea(state)
        for (area in listOf("kitchen", "office")) {
            assertEquals(BoxBrowser.cardsInArea(state, area), grouped[area], area)
        }
        assertEquals(null, grouped["nowhere"])
    }

    /**
     * The browser draws both pack numbers on every shelf at once, so it asks for them
     * all at once — and what it is told must be what each shelf's own control would do.
     */
    @Test
    fun theShelfCountsAgreeWithEachShelfsOwnControls() {
        var state = Box.state(
            (1..4).map { Box.word(it, area = "kitchen") } +
                (5..7).map { Box.word(it, area = "office") },
        )
        state = Box.inject(state, Box.sched("w01", dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w05", dueMillis = future, lastReviewMillis = now))
        state = BoxEngine.enqueue(state, listOf("w03", "w06"))

        val counts = BoxBrowser.shelfCounts(state)
        for (area in listOf("kitchen", "office")) {
            assertEquals(BoxBrowser.enqueueableCount(state, area), counts[area]?.packable, area)
            assertEquals(BoxBrowser.dequeueableCount(state, area), counts[area]?.queued, area)
        }
        assertEquals(ShelfCounts(packable = 2, queued = 1), counts["kitchen"])
        assertEquals(ShelfCounts(packable = 1, queued = 1), counts["office"])
    }

    /** A shelf with nothing left to pack and nothing queued drops out rather than reading zero. */
    @Test
    fun aShelfWithNothingToOfferIsAbsentFromTheCounts() {
        var state = Box.state(listOf(Box.word(1, area = "kitchen"), Box.word(2, area = "office")))
        state = Box.inject(state, Box.sched("w01", dueMillis = future, lastReviewMillis = now))

        assertEquals(null, BoxBrowser.shelfCounts(state)["kitchen"])
        assertEquals(ShelfCounts(packable = 1, queued = 0), BoxBrowser.shelfCounts(state)["office"])
    }

    @Test
    fun theConsolidatedFlagFollowsTheBarAndNeverThePhase() {
        var state = Box.state((1..5).map { Box.word(it) })
        state = Box.inject(
            state,
            Box.sched("w01", phase = CardPhase.Learning, stability = 0.5, dueMillis = future, lastReviewMillis = now),
        )
        // Review well under the consolidated bar (6.0) — the phase says nothing about it.
        state = Box.inject(state, Box.sched("w02", stability = 3.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(state, Box.sched("w03", stability = 9.0, dueMillis = future, lastReviewMillis = now))
        // Matured is a further rung, not a further mark: one bar, one flag.
        state = Box.inject(state, Box.sched("w04", stability = 99.0, dueMillis = future, lastReviewMillis = now))
        // Lapsed after consolidating: the bar has to be earned back.
        state = Box.inject(
            state,
            Box.sched(
                "w05", phase = CardPhase.Relearning, stability = 99.0,
                dueMillis = future, lastReviewMillis = now, lapses = 1,
            ),
        )

        fun row(id: String) = BoxBrowser.cardRowState(state, id, packOffered = false)
        assertEquals(CardRowState.Standing(CardPhase.Learning, false), row("w01"))
        assertEquals(CardRowState.Standing(CardPhase.Review, false), row("w02"))
        assertEquals(CardRowState.Standing(CardPhase.Review, true), row("w03"))
        assertEquals(CardRowState.Standing(CardPhase.Review, true), row("w04"))
        assertEquals(CardRowState.Standing(CardPhase.Relearning, false), row("w05"))
    }

    /**
     * The rung's color, so a row's badge and the shelf's bar read the same table:
     * both halves of the amber rung, the green one under the bar, teal above it.
     */
    @Test
    fun theRungsColorFollowsTheBarAndTheTwoAmberPhasesShareIt() {
        fun swatchOf(phase: CardPhase, consolidated: Boolean) =
            CardRowState.Standing(phase, consolidated).swatch

        assertEquals(Palette.amber, swatchOf(CardPhase.Learning, false))
        assertEquals(Palette.amber, swatchOf(CardPhase.Relearning, false))
        assertEquals(Palette.success, swatchOf(CardPhase.Review, false))
        assertEquals(Palette.grown, swatchOf(CardPhase.Review, true))
    }

    /** A schedule outlives a source switch; the card it belongs to may not join. */
    @Test
    fun aCardTheJoinDoesNotCarryHasNothingToState() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(state, Box.sched("w99", dueMillis = future, lastReviewMillis = now))

        assertEquals(CardRowState.Plain, BoxBrowser.cardRowState(state, "w99", packOffered = true))
    }
}
