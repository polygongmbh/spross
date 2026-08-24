package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization

class BoxSearchTests {

    private fun card(
        id: String,
        source: String,
        target: String,
        area: String = "kitchen",
        seedIndex: Int = 0,
        sourceSynonyms: List<String> = emptyList(),
        targetVariants: List<String> = emptyList(),
    ): Card = Card(
        id = id, kind = CardKind.Noun, area = area, emoji = null, seedIndex = seedIndex,
        components = emptyList(), feminineOf = null,
        source = Realization(lang = "de", text = source, synonyms = sourceSynonyms),
        target = Realization(lang = "sw", text = target, variants = targetVariants),
        promptFeminineMarker = false,
    )

    private val kitchen = SearchableArea("kitchen", "Küche")
    private val bedroom = SearchableArea("bedroom", "Schlafzimmer")
    private val areas = listOf(kitchen, bedroom)

    private fun state(vararg cards: Card): BoxState = Box.state(cards.toList())

    @Test
    fun findsOnTheKnownAndTheLearnedSide() {
        val box = state(
            card("fridge", "Kühlschrank", "friji", seedIndex = 1),
            card("spoon", "Löffel", "kijiko", seedIndex = 2),
        )
        assertEquals(listOf("fridge"), BoxSearch.search(box, areas, "Kühl").cards.map { it.id })
        assertEquals(listOf("spoon"), BoxSearch.search(box, areas, "kijiko").cards.map { it.id })
    }

    @Test
    fun matchingIsCaseInsensitiveAndTrimmed() {
        val box = state(card("fridge", "Kühlschrank", "friji"))
        assertEquals(listOf("fridge"), BoxSearch.search(box, areas, "  KÜHLSCHRANK ").cards.map { it.id })
    }

    @Test
    fun aBaseLetterQueryReachesTheAccentedSpelling() {
        val box = state(card("fridge", "Kühlschrank", "friji"))
        assertEquals(listOf("fridge"), BoxSearch.search(box, areas, "Kuhlschrank").cards.map { it.id })
        assertEquals(listOf("fridge"), BoxSearch.search(box, areas, "uhlsch").cards.map { it.id })
        assertEquals(listOf("kitchen"), BoxSearch.search(box, areas, "Kuche").areas.map { it.area })
    }

    @Test
    fun anAccentedQueryOnlyReachesTheAccentedSpelling() {
        val box = state(
            card("cake", "Kuchen", "keki", seedIndex = 1),
            card("table", "Küchentisch", "meza ya jikoni", seedIndex = 2),
        )
        assertEquals(listOf("table"), BoxSearch.search(box, areas, "Küche").cards.map { it.id })
    }

    @Test
    fun anSsQueryReachesTheSharpS() {
        val box = state(card("street", "Straße", "barabara"))
        assertEquals(listOf("street"), BoxSearch.search(box, areas, "Strasse").cards.map { it.id })
        assertEquals(listOf("street"), BoxSearch.search(box, areas, "rasse").cards.map { it.id })
    }

    @Test
    fun aSharpSQueryOnlyReachesTheSharpS() {
        val box = state(
            card("masses", "Massen", "umati", seedIndex = 1),
            card("street", "Straße", "barabara", seedIndex = 2),
        )
        assertTrue(BoxSearch.search(box, areas, "Maße").cards.isEmpty())
        assertEquals(listOf("street"), BoxSearch.search(box, areas, "Straße").cards.map { it.id })
    }

    @Test
    fun exactHitsLeadThenPrefixesThenTheRest() {
        val box = state(
            card("inside", "Handtuch", "taulo", seedIndex = 1),
            card("exact", "Hand", "mkono", seedIndex = 2),
            card("wordStart", "die linke Hand", "mkono wa kushoto", seedIndex = 3),
        )
        assertEquals(
            listOf("exact", "inside", "wordStart"),
            BoxSearch.search(box, areas, "Hand").cards.map { it.id },
        )
    }

    @Test
    fun sameRankKeepsSeedOrder() {
        val box = state(
            card("late", "Handschuh", "glavu", seedIndex = 9),
            card("early", "Handtuch", "taulo", seedIndex = 2),
        )
        assertEquals(listOf("early", "late"), BoxSearch.search(box, areas, "Hand").cards.map { it.id })
    }

    @Test
    fun aHeadwordOutranksAnAlternate() {
        val box = state(
            card("viaSynonym", "Kühlgerät", "friji", sourceSynonyms = listOf("Eisschrank"), seedIndex = 1),
            card("viaHeadword", "Eisschrank", "friji ndogo", seedIndex = 9),
        )
        assertEquals(
            listOf("viaHeadword", "viaSynonym"),
            BoxSearch.search(box, areas, "Eisschrank").cards.map { it.id },
        )
    }

    @Test
    fun acceptedVariantsAreFindable() {
        val box = state(card("photo", "Foto", "picha", targetVariants = listOf("pikcha")))
        assertEquals(listOf("photo"), BoxSearch.search(box, areas, "pikcha").cards.map { it.id })
    }

    @Test
    fun areasMatchOnTheirHeading() {
        val box = state(card("fridge", "Kühlschrank", "friji"))
        val found = BoxSearch.search(box, areas, "Küche")
        assertEquals(listOf("kitchen"), found.areas.map { it.area })
        // why: the area's own heading must not drag its words in as word hits.
        assertTrue(found.cards.isEmpty())
    }

    @Test
    fun anEmptyQueryFindsNothingRatherThanEverything() {
        val box = state(card("fridge", "Kühlschrank", "friji"))
        val found = BoxSearch.search(box, areas, "   ")
        assertTrue(found.isEmpty)
    }

    @Test
    fun aMissIsEmptyOnBothLists() {
        val box = state(card("fridge", "Kühlschrank", "friji"))
        assertTrue(BoxSearch.search(box, areas, "Regenschirm").isEmpty)
    }

    @Test
    fun resultsAreCapped() {
        val cards = (1..CARD_OVERFLOW).map { card("w$it", "Wort$it", "neno$it", seedIndex = it) }
        val found = BoxSearch.search(Box.state(cards), areas, "Wort")
        assertEquals(BoxSearch.CARD_LIMIT, found.cards.size)
        // The cap keeps the seed-ordered head, never an arbitrary slice.
        assertEquals("w1", found.cards.first().id)
    }

    private companion object {
        const val CARD_OVERFLOW = BoxSearch.CARD_LIMIT + 5
    }
}
