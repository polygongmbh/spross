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
 * Browsing the box: which shelves are listed, which one opens, what queuing a shelf would take in,
 * and what a single row has to state about its card.
 */
class BoxBrowserTest {
    private val now = Box.day1
    private val future = Box.plusDays(now, 5.0)

    /**
     * Three groups over four areas: `home` titled for the reader, `work` in English only,
     * `wild` titled in no language at all — the three steps of the heading fallback.
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
    fun aNamedAreaOpensWithItsGroupInsteadOfWhatStoodOpen() {
        val state = Box.state(listOf(Box.word(1, area = "kitchen"), Box.word(2, area = "office")))
        val opening = BoxFold.opening(sections(state), stats(state), revealArea = "office")
        assertEquals(BoxFold(setOf("work"), setOf("office")), opening)

        val revealed = BoxFold(setOf("home", "work"), setOf("office", "kitchen")).revealing("kitchen", sections(state))
        assertEquals(BoxFold(setOf("home"), setOf("kitchen")), revealed)
        assertEquals(revealed, revealed.revealing(OwnWords.AREA, sections(state)))
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
    fun queuingCountsOnlyWhatTheEngineWouldTakeIn() {
        var state = Box.state((1..3).map { Box.word(it, area = "kitchen") } + Box.word(4, area = "office"))
        state = Box.inject(state, Box.sched("w01", dueMillis = future, lastReviewMillis = now))
        state = BoxEngine.queue(state, listOf("w03"))

        // w01 is already scheduled, w03 already queued — only w02 is left to add.
        assertEquals(listOf("w02"), BoxBrowser.queueableCardIds(state, "kitchen"))
        assertEquals(1, BoxBrowser.queueableCount(state, "kitchen"))

        // The count and the queuing read the same predicate, so queuing the shelf empties it.
        val queued = BoxEngine.queue(state, BoxBrowser.queueableCardIds(state, "kitchen"))
        assertEquals(0, BoxBrowser.queueableCount(queued, "kitchen"))
        assertEquals(1, BoxBrowser.queueableCount(queued, "office"))
    }

    @Test
    fun aSuspendedCardOffersUnsuspendingWhateverTheRowStandsIn() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(
            state,
            Box.sched("w01", dueMillis = future, lastReviewMillis = now, suspended = true),
        )

        assertEquals(CardRowState.Suspended, BoxBrowser.cardRowState(state, "w01", queueOffered = false))
        assertEquals(CardRowState.Suspended, BoxBrowser.cardRowState(state, "w01", queueOffered = true))
    }

    /**
     * A row queues or unqueues itself, alone, only in a search result — where the learner
     * reached this one card by typing its name rather than browsing a shelf
     * ([queueOffered] true). A shelf listing queues and unqueues its own queue in a batch
     * instead. A word already queued always states so, but
     * [CardRowState.Queued.removalOffered] follows [queueOffered] the same way
     * [CardRowState.QueueOffered] does.
     */
    @Test
    fun theOfferToQueueAndUnqueueIsPerWordOnlyWhereWordsAreQueuedOneAtATime() {
        var state = Box.state(listOf(Box.word(1), Box.word(2)))
        state = BoxEngine.queue(state, listOf("w02"))

        assertEquals(CardRowState.QueueOffered, BoxBrowser.cardRowState(state, "w01", queueOffered = true))
        assertEquals(
            CardRowState.Queued(removalOffered = true),
            BoxBrowser.cardRowState(state, "w02", queueOffered = true),
        )
        // No per-word offer, and new is silence: an unqueued card states nothing.
        assertEquals(CardRowState.Plain, BoxBrowser.cardRowState(state, "w01", queueOffered = false))
        // A queued one still says so, but the area listing's shelf queues and unqueues
        // in a batch — the row itself offers nothing.
        assertEquals(
            CardRowState.Queued(removalOffered = false),
            BoxBrowser.cardRowState(state, "w02", queueOffered = false),
        )
    }

    @Test
    fun unqueueableCardsAreTheAreasQueuedCards() {
        var state = Box.state((1..3).map { Box.word(it, area = "kitchen") } + Box.word(4, area = "office"))
        state = BoxEngine.queue(state, listOf("w01", "w03", "w04"))

        // Seed order, not queue order: a shelf listing reads like the shelf, not the queue.
        assertEquals(listOf("w01", "w03"), BoxBrowser.unqueueableCardIds(state, "kitchen"))
        assertEquals(2, BoxBrowser.unqueueableCount(state, "kitchen"))
        assertEquals(listOf("w04"), BoxBrowser.unqueueableCardIds(state, "office"))
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
     * The browser draws both queue numbers on every shelf at once, so it asks for them
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
        state = BoxEngine.queue(state, listOf("w03", "w06"))

        val counts = BoxBrowser.shelfCounts(state)
        for (area in listOf("kitchen", "office")) {
            assertEquals(BoxBrowser.queueableCount(state, area), counts[area]?.queueable, area)
            assertEquals(BoxBrowser.unqueueableCount(state, area), counts[area]?.queued, area)
        }
        assertEquals(ShelfCounts(queueable = 2, queued = 1), counts["kitchen"])
        assertEquals(ShelfCounts(queueable = 1, queued = 1), counts["office"])
    }

    /** A shelf with nothing left to queue and nothing queued drops out rather than reading zero. */
    @Test
    fun aShelfWithNothingToOfferIsAbsentFromTheCounts() {
        var state = Box.state(listOf(Box.word(1, area = "kitchen"), Box.word(2, area = "office")))
        state = Box.inject(state, Box.sched("w01", dueMillis = future, lastReviewMillis = now))

        assertEquals(null, BoxBrowser.shelfCounts(state)["kitchen"])
        assertEquals(ShelfCounts(queueable = 1, queued = 0), BoxBrowser.shelfCounts(state)["office"])
    }

    @Test
    fun theStageFollowsTheGrowthLadderNeverTheRawPhase() {
        // The ladder itself is GrowthStageTests'; a row carries the stage it hands out.
        var state = Box.state((1..2).map { Box.word(it) })
        // Review well under the growing bar — the phase says nothing about it.
        state = Box.inject(state, Box.sched("w01", stability = 3.0, dueMillis = future, lastReviewMillis = now))
        state = Box.inject(
            state,
            Box.sched("w02", phase = CardPhase.Relearning, stability = 2.0, dueMillis = future, lastReviewMillis = now),
        )

        fun row(id: String) = BoxBrowser.cardRowState(state, id, queueOffered = false)
        assertEquals(CardRowState.Standing(ActiveStage.Fresh), row("w01"))
        assertEquals(CardRowState.Standing(ActiveStage.Lapsed), row("w02"))
    }

    /**
     * The stage's color, so a row's badge and the shelf's bar read the same table:
     * amber for Fresh/Lapsed, green for Growing, jade for Settled.
     */
    @Test
    fun theStageColorFollowsTheBarAndTheAmberStagesShareIt() {
        fun swatchOf(stage: ActiveStage) = CardRowState.Standing(stage).swatch

        assertEquals(swatchOf(ActiveStage.Fresh), swatchOf(ActiveStage.Lapsed))
        assertEquals(Palette.success, swatchOf(ActiveStage.Growing))
        assertEquals(Palette.settled, swatchOf(ActiveStage.Settled))
    }

    /** A schedule outlives a source switch; the card it belongs to may not join. */
    @Test
    fun aCardTheJoinDoesNotCarryHasNothingToState() {
        var state = Box.state(listOf(Box.word(1)))
        state = Box.inject(state, Box.sched("w99", dueMillis = future, lastReviewMillis = now))

        assertEquals(CardRowState.Plain, BoxBrowser.cardRowState(state, "w99", queueOffered = true))
    }
}
