package net.spross.kern.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.box.DayPart

/**
 * The day's greetings: which concept each stretch reaches for, and how a name joins the
 * sentence. A catalog of its own rather than the shared [Fixture] — de authors all four
 * stretches, sw only the morning, which is the coverage hole a surface has to survive.
 */
class GreetingTests {

    private val catalog = Catalog.load(
        MapCatalogSource(
            mapOf(
                "areas.json" to """
                    [ { "group": "start", "titles": { "de": "Start", "sw": "Mwanzo" },
                        "areas": [{ "area": "alpha", "emoji": "🅰️" }] } ]
                """.trimIndent(),
                "languages.json" to """
                    { "de": { "name": "Deutsch", "englishName": "German", "flag": "🇩🇪" },
                      "fr": { "name": "Français", "englishName": "French", "flag": "🇫🇷" },
                      "sw": { "name": "Kiswahili", "englishName": "Swahili", "flag": "🇹🇿" } }
                """.trimIndent(),
                "alpha/concepts.json" to """
                    [ { "slug": "ready-to-learn", "kind": "phrase", "components": [] },
                      { "slug": "one-more-word", "kind": "phrase", "components": [] },
                      { "slug": "good-morning", "kind": "phrase", "components": [] },
                      { "slug": "good-day", "kind": "phrase", "components": [] },
                      { "slug": "good-evening", "kind": "phrase", "components": [] },
                      { "slug": "good-night", "kind": "phrase", "components": [] } ]
                """.trimIndent(),
                "alpha/de.json" to """
                    { "title": "Alpha",
                      "words": { "ready-to-learn": { "text": "Bereit zu lernen?" },
                                 "one-more-word": { "text": "Noch ein Wort?" },
                                 "good-morning": { "text": "Guten Morgen!" },
                                 "good-day": { "text": "Guten Tag!" },
                                 "good-evening": { "text": "Guten Abend!" },
                                 "good-night": { "text": "Gute Nacht!" } } }
                """.trimIndent(),
                "alpha/sw.json" to """
                    { "title": "Alpha",
                      "words": { "good-morning": { "text": "Habari za asubuhi!" },
                                 "one-more-word": { "text": "Neno moja zaidi?" } } }
                """.trimIndent(),
                // French leaves the morning unauthored: "Bonjour !" already is it.
                "alpha/fr.json" to """
                    { "title": "Alpha",
                      "words": { "good-day": { "text": "Bonjour !" } } }
                """.trimIndent(),
            ),
        ),
    )

    @Test
    fun everyStretchOfTheDayReachesItsOwnGreeting() {
        assertEquals("Guten Morgen!", catalog.greeting("de", DayPart.Morning))
        assertEquals("Guten Tag!", catalog.greeting("de", DayPart.Day))
        assertEquals("Guten Abend!", catalog.greeting("de", DayPart.Evening))
        assertEquals("Gute Nacht!", catalog.greeting("de", DayPart.Night))
    }

    @Test
    fun aLanguageThatGreetsTheStretchWithNothingReturnsNull() {
        assertEquals("Habari za asubuhi!", catalog.greeting("sw", DayPart.Morning))
        assertNull(catalog.greeting("sw", DayPart.Night))
    }

    @Test
    fun aMorningOfItsOwnIsOptionalBecauseTheAllDayGreetingIsOne() {
        assertEquals("Bonjour !", catalog.greeting("fr", DayPart.Morning))
        // Only the morning borrows: an evening no language authors stays unsaid.
        assertNull(catalog.greeting("fr", DayPart.Evening))
    }

    @Test
    fun theNameGoesInsideTheSentenceAndTheMarkFollowsIt() {
        assertEquals("Habari za asubuhi, Tim!", catalog.greeting("sw", DayPart.Morning, "Tim"))
        assertEquals("Alles gut, Tim.", Greetings.addressed("Alles gut.", "Tim"))
        assertEquals("Wie geht es, Tim?", Greetings.addressed("Wie geht es?", "Tim"))
        assertEquals("Bonsoir, Tim !", Greetings.addressed("Bonsoir !", "Tim"))
        assertEquals("Habari, Tim", Greetings.addressed("Habari", "Tim"))
    }

    @Test
    fun theHourLeadsAndTheHourFreePhrasesFollow() {
        assertEquals(
            listOf("Guten Morgen, Tim!", "Bereit zu lernen, Tim?", "Noch ein Wort, Tim?"),
            catalog.spokenLines("de", DayPart.Morning, "Tim"),
        )
        // A language realizing only some of them offers only those, in the same order.
        assertEquals(
            listOf("Habari za asubuhi!", "Neno moja zaidi?"),
            catalog.spokenLines("sw", DayPart.Morning),
        )
        // Nothing to say at all is an empty list, not a line in another language.
        assertEquals(emptyList(), catalog.spokenLines("sw", DayPart.Evening).drop(1))
    }

    @Test
    fun noNameLeavesTheGreetingAsAuthored() {
        assertEquals("Guten Morgen!", catalog.greeting("de", DayPart.Morning, null))
        assertEquals("Guten Morgen!", Greetings.addressed("Guten Morgen!", "   "))
    }
}
