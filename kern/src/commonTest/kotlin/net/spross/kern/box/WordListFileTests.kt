package net.spross.kern.box

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reading another app's word list, or a saved chat answer, into own words. */
class WordListFileTests {

    /** A box teaching sw `t1`…`t3` from de `g1`…`g3`. */
    private val box = Box.state((1..3).map { Box.word(it) })

    private fun pairs(text: String): List<BriefWord> = WordListFile.read(text, box).map { it.word }

    /** Quizlet's default export: a tab between the sides, extra columns dropped. */
    @Test
    fun readsTabSeparatedRows() {
        assertEquals(
            listOf(BriefWord("mgeni", "Gast"), BriefWord("ratiba", "Fahrplan")),
            pairs("mgeni\tGast\tlesson 2\nratiba\tFahrplan\n"),
        )
    }

    /** Anki's plain-text export: header lines skipped, HTML read as text. */
    @Test
    fun readsAnAnkiExport() {
        val anki = "#separator:tab\n#html:true\n<b>mgeni</b>\tder&nbsp;Gast<br>\n"
        assertEquals(listOf(BriefWord("mgeni", "der Gast")), pairs(anki))
    }

    /** A spreadsheet's CSV: a quoted field keeps its comma. */
    @Test
    fun readsQuotedCsv() {
        assertEquals(
            listOf(BriefWord("kwenda", "gehen, laufen"), BriefWord("mgeni", "Gast")),
            pairs("kwenda,\"gehen, laufen\"\nmgeni,Gast\n"),
        )
    }

    /** A chat answer saved to a file reads as it would pasted. */
    @Test
    fun readsTheChatFormat() {
        assertEquals(listOf(BriefWord("mgeni", "Gast")), pairs("Here you go:\n```spross\nmgeni = Gast\n```\n"))
    }

    /** A list written known-language first is turned when the box knows the second column's words. */
    @Test
    fun turnsAListWrittenTheOtherWayRound() {
        val found = WordListFile.read("g1;t1\nGast;mgeni\n", box)
        assertEquals(listOf(BriefWord("mgeni", "Gast"), BriefWord("t1", "g1")), found.map { it.word })
        assertEquals(HarvestKind.Held, found.last().kind)
    }

    /** No cap: the learner picked the file. */
    @Test
    fun readsPastThePasteLimit() {
        val many = (1..Harvest.MAX_WORDS * 2).joinToString("\n") { "neno$it\tWort$it" }
        assertTrue(WordListFile.read(many, box).size == Harvest.MAX_WORDS * 2)
    }

    /** Many kept at once land queued, each under its own id. */
    @Test
    fun keepsAListInOneWrite() {
        val kept = Harvest.keep(box, listOf(BriefWord("mgeni", "Gast"), BriefWord("Mgeni", "Besuch")), Box.day1)
        assertEquals(2, kept.ownWords.map { it.id }.toSet().size)
        assertTrue(kept.queued.containsAll(kept.ownWords.map { it.id }))
    }
}
