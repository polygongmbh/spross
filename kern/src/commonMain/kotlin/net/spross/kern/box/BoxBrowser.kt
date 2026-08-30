package net.spross.kern.box

import net.spross.kern.catalog.Catalog
import net.spross.kern.model.Card
import net.spross.kern.model.CardPhase
import net.spross.kern.model.Language

/**
 * One shelf of the box as the browser lists it:
 * an `areas.json` group with the areas this profile actually holds cards in.
 */
data class AreaGroupSection(
    val id: String,
    /** The group's heading, already resolved for the reader — see [BoxBrowser.sections]. */
    val title: String,
    /** The group's areas in manifest order, filtered to the ones the box holds. */
    val areas: List<String>,
)

/**
 * What a shelf's two pack controls have to offer, counted for every area at once.
 *
 * The browser draws both numbers on every shelf it lists, and asked one shelf at a
 * time each answer is a walk of the whole box ([enqueueableCardIds] filters
 * [cardsInArea]) — so a screenful of shelves costs areas x cards. [BoxBrowser.shelfCounts]
 * answers them all in one pass instead, under the same predicates the id lists use.
 */
data class ShelfCounts(
    /** What packing this shelf would add — the size of [BoxBrowser.enqueueableCardIds]. */
    val packable: Int,
    /** What taking its queue back out would remove — the size of [BoxBrowser.dequeueableCardIds]. */
    val queued: Int,
)

/**
 * What one listed card says about itself besides the word — the rule, never the mark.
 *
 * Exactly one of these holds at a time, and which one is a box question:
 * whether the card sleeps, whether it can still be packed, and which bars it has cleared.
 * What a surface draws for each (an icon, a capsule, a pill, or nothing at all) is its own affair.
 */
sealed class CardRowState {
    /** Out of rotation; the one thing left to offer is waking it. */
    data object Sleeping : CardRowState()

    /** Unscheduled, and the row stands where single words can be packed — the offer holds. */
    data object PackOffered : CardRowState()

    /**
     * Already packed, waiting for a round to bring it in — shown wherever the row stands.
     * [removalOffered] mirrors [PackOffered]'s own gate: a word packed by name
     * ([BoxEngine.dequeue]) is taken back out by name the same way; an area listing takes
     * whole batches out through its own control ([BoxEngine.dequeueArea]) instead,
     * mirroring how it takes them in.
     */
    data class Packed(val removalOffered: Boolean) : CardRowState()

    /**
     * Nothing to state: a card with no exposure behind it, outside any pack context.
     * New is the ABSENCE of a standing, not a standing of its own —
     * in a shelf of unstarted words a "new" badge would be most of the rows.
     */
    data object Plain : CardRowState()

    /**
     * The card is on the ladder, and this is where.
     * [phase] is never [CardPhase.New]: a card with nothing behind it is [Plain] or [PackOffered].
     *
     * [consolidated] travels BESIDE the phase rather than being read out of it.
     * A card reaches Review well below [net.spross.kern.model.BoxConfig.consolidatedStability],
     * so a mark keyed to the phase would seal cards the area's consolidated count leaves out,
     * and a row would disagree with the shelf above it on sight.
     * Whatever a surface shows for "this word has landed", it takes it from here.
     */
    data class Standing(val phase: CardPhase, val consolidated: Boolean) : CardRowState()
}

/**
 * Reading the box as a browsable list: which shelves exist, in which order,
 * which one opens first, and what each row and each pack control has to say.
 *
 * Ordering and grouping are content rules (the catalog's manifest) crossed with box rules
 * (which areas hold cards, which cards are active) — neither of them a layout,
 * so a surface asks here rather than walking `state.cards` with rules of its own.
 */
object BoxBrowser {

    /**
     * The areas the browser lists, in catalog default order:
     * the manifest's own order intersected with the areas this profile holds cards in.
     *
     * The learner's own words ([OwnWords.AREA]) come LAST and belong to no group —
     * the manifest cannot list an area the catalog does not own,
     * and their seed order puts them behind every catalog word anyway.
     * Their heading is chrome rather than catalog content:
     * kern hands back the area key, the app names it in the reader's UI language.
     */
    fun areaNames(catalog: Catalog, stats: BoxStatistics): List<String> {
        val present = stats.areas.mapTo(mutableSetOf()) { it.name }
        val fromCatalog = catalog.areaNames.filter { it in present }
        return if (OwnWords.AREA in present) fromCatalog + OwnWords.AREA else fromCatalog
    }

    /**
     * The catalog's groups in manifest order, each filtered to the areas [areaNames] lists;
     * a group left holding none of them drops out rather than standing empty.
     *
     * Titles read in [source], then [Catalog.FALLBACK_SOURCE], then the group id itself —
     * a manifest that forgot one language still names its shelf,
     * and a shelf named by its id is a visible content bug rather than a blank heading.
     */
    fun sections(catalog: Catalog, stats: BoxStatistics, source: Language): List<AreaGroupSection> {
        val present = areaNames(catalog, stats).toSet()
        return catalog.groups.mapNotNull { group ->
            val areas = group.areas.filter { it in present }
            if (areas.isEmpty()) return@mapNotNull null
            AreaGroupSection(
                id = group.id,
                title = group.titles[source]
                    ?: group.titles[Catalog.FALLBACK_SOURCE]
                    ?: group.id.replaceFirstChar { it.uppercaseChar() },
                areas = areas,
            )
        }
    }

    /**
     * The section the browser opens on: the first one holding an area with active cards —
     * where the learner left off, and the only shelf whose numbers have anything to say yet.
     *
     * A box nothing has been started in opens its first section instead,
     * so the browser never opens fully folded.
     * Null only when there is no section at all.
     */
    fun defaultExpandedGroupId(sections: List<AreaGroupSection>, stats: BoxStatistics): String? {
        val started = stats.areas.filter { it.active > 0 }.mapTo(mutableSetOf()) { it.name }
        val openAt = sections.firstOrNull { section -> section.areas.any { it in started } }
        return (openAt ?: sections.firstOrNull())?.id
    }

    /** The area's cards in seed order — the shelf as the box itself lists it. */
    fun cardsInArea(state: BoxState, area: String): List<Card> =
        state.cards.values.filter { it.area == area }.sortedWith(Inventory.seedOrder)

    /**
     * The area's cards a pack would take in, in seed order: unscheduled, and not already queued.
     * These are [BoxEngine.enqueue]'s own guards asked in advance,
     * so hand this list straight to it — a count derived by one rule and a pack performed
     * under another is how a shelf comes to promise a number it does not add.
     *
     * The AREA's cards only: enqueuing a phrase also prepends the components it is missing,
     * and where those live on another shelf the pack takes in more than this lists.
     */
    fun enqueueableCardIds(state: BoxState, area: String): List<String> {
        val queued = state.enqueued.toSet()
        return cardsInArea(state, area)
            .filter { state.scheduling[it.id] == null && it.id !in queued }
            .map { it.id }
    }

    /** What packing this shelf would add — the size of [enqueueableCardIds]. */
    fun enqueueableCount(state: BoxState, area: String): Int = enqueueableCardIds(state, area).size

    /**
     * The area's cards a [BoxEngine.dequeueArea] would take back out, in seed order:
     * queued, and belonging to this area — [BoxEngine.dequeueArea]'s own guard asked
     * in advance, same as [enqueueableCardIds] is for [BoxEngine.enqueue].
     */
    fun dequeueableCardIds(state: BoxState, area: String): List<String> {
        val inArea = cardsInArea(state, area).mapTo(mutableSetOf()) { it.id }
        return state.enqueued.filter { it in inArea }
    }

    /** What taking this shelf's queue back out would remove — the size of [dequeueableCardIds]. */
    fun dequeueableCount(state: BoxState, area: String): Int = dequeueableCardIds(state, area).size

    /**
     * Both pack counts for every area the box holds cards in, in one walk.
     *
     * The same predicates [enqueueableCardIds] and [dequeueableCardIds] apply, read off
     * the cards rather than off the areas: a browser listing thirty shelves asks once
     * instead of sixty times, and no shelf can promise a number its own pack would not add.
     * An area with nothing to offer on either control is absent rather than zeroed.
     */
    fun shelfCounts(state: BoxState): Map<String, ShelfCounts> {
        val queued = state.enqueued.toSet()
        val packable = mutableMapOf<String, Int>()
        val waiting = mutableMapOf<String, Int>()
        for (card in state.cards.values) {
            if (card.id in queued) {
                waiting[card.area] = (waiting[card.area] ?: 0) + 1
            } else if (state.scheduling[card.id] == null) {
                packable[card.area] = (packable[card.area] ?: 0) + 1
            }
        }
        return (packable.keys + waiting.keys).associateWith {
            ShelfCounts(packable = packable[it] ?: 0, queued = waiting[it] ?: 0)
        }
    }

    /**
     * What this card's row has to state, and nothing about how it is drawn.
     *
     * [packOffered] says the row stands in a context that packs (and unpacks) a SINGLE
     * word — a search hit, which the learner went looking for by name — and gates
     * [CardRowState.Packed.removalOffered] the same way. An area listing packs and
     * unpacks through the shelf's own control instead
     * ([enqueueableCardIds]/[dequeueableCardIds]), so an unqueued card there states
     * nothing at all.
     *
     * Read off the growth ladder ([GrowthStage]) and [Statistics.isConsolidated],
     * never off the raw phase: those two are where "which bars has this card cleared"
     * is already answered, and a second derivation is a second answer waiting to disagree.
     * A card the current join does not carry has no standing in the box and reads [CardRowState.Plain].
     */
    fun cardRowState(state: BoxState, cardId: String, packOffered: Boolean): CardRowState {
        if (state.cards[cardId] == null) return CardRowState.Plain
        val sched = state.scheduling[cardId] ?: return when {
            cardId in state.enqueued -> CardRowState.Packed(removalOffered = packOffered)
            !packOffered -> CardRowState.Plain
            else -> CardRowState.PackOffered
        }
        return when (stageOf(state, sched)) {
            GrowthStage.Suspended -> CardRowState.Sleeping
            GrowthStage.Learning -> CardRowState.Standing(CardPhase.Learning, false)
            GrowthStage.Relearning -> CardRowState.Standing(CardPhase.Relearning, false)
            // The Review rungs — Fresh, Consolidated, Matured — differ only in which bars
            // they have cleared, and the seal follows the consolidated one.
            else -> CardRowState.Standing(CardPhase.Review, Statistics.isConsolidated(state, sched))
        }
    }
}
