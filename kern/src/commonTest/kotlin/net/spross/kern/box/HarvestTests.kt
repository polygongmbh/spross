package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.Realization

/** Reading a conversation's answer back into the box — what survives an assistant. */
class HarvestTests {

    private fun state() = Box.state((1..3).map { Box.word(it, area = "alpha") })

    /** The fence is where the answer is; the prose around it is conversation. */
    @Test
    fun readsTheFencedBlockAndNotTheChatAroundIt() {
        val paste = """
            Nice work! Here is what you picked up. Ratiba = timetable was the hard one.

            ```spross
            mgeni = Gast
            ratiba = Fahrplan
            ```

            Sleep well — kwaheri = Tschüss!
        """.trimIndent()

        assertEquals(
            listOf(BriefWord("mgeni", "Gast"), BriefWord("ratiba", "Fahrplan")),
            Harvest.read(paste, state()),
        )
    }

    /** An assistant that dropped the fence still answered the question. */
    @Test
    fun readsAnUnfencedListToo() {
        val paste = """
            Words you met:
            - mgeni = Gast
            2. `ratiba` = Fahrplan
            → kuchelewa → sich verspäten
        """.trimIndent()

        assertEquals(
            listOf(
                BriefWord("mgeni", "Gast"),
                BriefWord("ratiba", "Fahrplan"),
                BriefWord("kuchelewa", "sich verspäten"),
            ),
            Harvest.read(paste, state()),
        )
    }

    /** A fence of another kind is somebody else's code, not our answer. */
    @Test
    fun ignoresAFenceThatIsNotOurs() {
        val paste = """
            ```python
            print("mgeni = Gast")
            ```

            ```spross
            ratiba = Fahrplan
            ```
        """.trimIndent()

        assertEquals(listOf(BriefWord("ratiba", "Fahrplan")), Harvest.read(paste, state()))
    }

    /** A word the box already teaches is the assistant's mistake to absorb. */
    @Test
    fun dropsWordsTheBoxAlreadyHolds() {
        val paste = "```spross\nt1 = g1\nmgeni = Gast\nMGENI = Gast\n```"

        assertEquals(listOf(BriefWord("mgeni", "Gast")), Harvest.read(paste, state()))
    }

    /** Written back with the article it was taught with, it is still the same word. */
    @Test
    fun dropsAWordHandedBackWithItsArticle() {
        val card = Box.word(1, area = "alpha").let {
            it.copy(target = Realization(lang = "sw", text = "Amt", grammar = mapOf("gender" to "das")))
        }
        val paste = "```spross\ndas Amt = office\n```"

        assertTrue(Harvest.read(paste, Box.state(listOf(card))).isEmpty())
    }

    /** Answering with a dictionary is a misunderstanding, not a windfall. */
    @Test
    fun stopsAtTheCapOnOnePaste() {
        val lines = (1..Harvest.MAX_WORDS + 20).joinToString("\n") { "neno$it = Wort$it" }

        assertEquals(Harvest.MAX_WORDS, Harvest.read("```spross\n$lines\n```", state()).size)
    }

    /** Explanations are not words; the line length is what tells them apart. */
    @Test
    fun skipsLinesThatAreSentences() {
        val paste = """
            ```spross
            mgeni = Gast
            kuchelewa = to be late, which you used when you talked about the bus this morning
            ```
        """.trimIndent()

        assertEquals(listOf(BriefWord("mgeni", "Gast")), Harvest.read(paste, state()))
    }

    /** Which side is which language is the join's answer, read back off the same brief. */
    @Test
    fun landsBothHalvesInTheRightLanguages() {
        val word = Harvest.ownWord(state(), BriefWord("mgeni", "Gast"))

        assertEquals("Gast", word.texts["de"])
        assertEquals("mgeni", word.texts["sw"])
        assertTrue(OwnWords.owns(word.id))
    }
}
