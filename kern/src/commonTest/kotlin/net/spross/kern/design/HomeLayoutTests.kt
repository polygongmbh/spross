package net.spross.kern.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.design.HomeSection.Conversation
import net.spross.kern.design.HomeSection.Day
import net.spross.kern.design.HomeSection.Drills
import net.spross.kern.design.HomeSection.Header
import net.spross.kern.design.HomeSection.Listening
import net.spross.kern.design.HomeSection.Trees
import net.spross.kern.design.HomeSection.VoiceUpgrade

class HomeLayoutTests {

    @Test
    fun oneColumnHoldsEverySectionInThePhonesOrder() {
        val columns = HomeColumns(split = false)
        assertEquals(listOf(Header, VoiceUpgrade, Day, Listening, Drills, Conversation, Trees), columns.primary)
        assertTrue(columns.secondary.isEmpty())
    }

    @Test
    fun aSplitWindowDealsEachRolesSectionsIntoItsPaneInOrder() {
        val columns = HomeColumns(split = true)
        assertEquals(listOf(Day, Drills, Conversation), columns.primary)
        assertEquals(listOf(Header, VoiceUpgrade, Listening, Trees), columns.secondary)
    }
}
