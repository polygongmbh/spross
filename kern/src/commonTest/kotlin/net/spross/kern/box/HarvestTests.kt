package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.Card
import net.spross.kern.model.Realization

/** Reading a conversation's answer back into the box — what survives an assistant. */
class HarvestTests {

    private fun state() = Box.state((1..3).map { Box.word(it, area = "alpha") })

    /** A card written in the words a real conversation would hand back. */
    private fun card(target: String, source: String, gender: String? = null): Card =
        Box.word(1, area = "alpha").copy(
            source = Realization(lang = "de", text = source),
            target = Realization(
                lang = "sw",
                text = target,
                grammar = gender?.let { mapOf("gender" to it) } ?: emptyMap(),
            ),
        )

    private fun pairs(text: String, state: BoxState = state()): List<BriefWord> =
        Harvest.read(text, state).map { it.word }

    private fun one(text: String, state: BoxState): HarvestWord = Harvest.read(text, state).single()

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

        assertEquals(listOf(BriefWord("mgeni", "Gast"), BriefWord("ratiba", "Fahrplan")), pairs(paste))
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
            pairs(paste),
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

        assertEquals(listOf(BriefWord("ratiba", "Fahrplan")), pairs(paste))
    }

    /** A word the box already teaches comes home named, not dropped. */
    @Test
    fun marksWordsTheBoxAlreadyHolds() {
        val found = one("```spross\nT1 = g1\n```", state())

        assertEquals(HarvestKind.Held, found.kind)
        assertEquals("t1", found.match)
    }

    /** Written back with the article it was taught with, it is still the same word. */
    @Test
    fun holdsAWordHandedBackWithItsArticle() {
        val box = Box.state(listOf(card(target = "Amt", source = "Behörde", gender = "das")))

        assertEquals(HarvestKind.Held, one("```spross\ndas Amt = office\n```", box).kind)
    }

    /**
     * An agglutinating language hands back a whole phrase as one word, and the word the box
     * teaches is sitting inside it.
     */
    @Test
    fun findsATaughtWordInsideALongerOne() {
        val box = Box.state(listOf(card(target = "penda", source = "mögen")))
        val found = one("```spross\nninapenda = ich liebe es\n```", box)

        assertEquals(HarvestKind.Near, found.kind)
        assertEquals("penda", found.match)
    }

    /** What the target side hides, the language the learner reads still says. */
    @Test
    fun findsTheOverlapOnTheGlossWhenTheSpellingHidesIt() {
        val box = Box.state(listOf(card(target = "penda", source = "lieben")))
        val found = one("```spross\nnapendezwa = lieben und geliebt werden\n```", box)

        assertEquals(HarvestKind.Near, found.kind)
        assertEquals("penda", found.match)
    }

    /** One letter apart is a second card teaching the same word. */
    @Test
    fun findsASpellingASlipOff() {
        val box = Box.state(listOf(card(target = "ratiba", source = "Fahrplan")))

        assertEquals(HarvestKind.Near, one("```spross\nratibu = Zeitplan\n```", box).kind)
    }

    /** A word that shares nothing with the box is what the learner asked the chat for. */
    @Test
    fun leavesAnUnrelatedWordNew() {
        val box = Box.state(listOf(card(target = "penda", source = "mögen")))
        val found = one("```spross\nkiatu = Schuh\n```", box)

        assertEquals(HarvestKind.New, found.kind)
        assertEquals(null, found.match)
    }

    /** New first, then near, then held — one list a surface can walk and head. */
    @Test
    fun groupsTheThreeKindsInKeepingOrder() {
        val box = Box.state(listOf(card(target = "penda", source = "mögen")))
        val paste = "```spross\npenda = mögen\nninapenda = ich mag es\nkiatu = Schuh\n```"

        assertEquals(
            listOf(HarvestKind.New, HarvestKind.Near, HarvestKind.Held),
            Harvest.read(paste, box).map { it.kind },
        )
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

        assertEquals(listOf(BriefWord("mgeni", "Gast")), pairs(paste))
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
