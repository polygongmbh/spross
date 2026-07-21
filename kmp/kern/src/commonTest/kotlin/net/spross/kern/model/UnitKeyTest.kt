package net.spross.kern.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class UnitKeyTest {
    @Test
    fun produceEncoding() {
        assertEquals("kitchen/fridge|produce", UnitKey.produce("kitchen/fridge").encoded)
    }

    @Test
    fun recognizeEncodingNormalizesForm() {
        assertEquals("a/b|recognize|Ha llo", UnitKey.recognize("a/b", "  Ha \n llo ").encoded)
    }

    @Test
    fun formKeyStripsPipesAndCollapsesWhitespace() {
        assertEquals("ab", UnitKey.formKey("a|b"))
        assertEquals("миша мишка", UnitKey.formKey(" миша\n\tмишка "))
    }

    @Test
    fun formKeyAppliesNfc() {
        assertEquals("Tür", UnitKey.formKey("Tür"))
    }

    @Test
    fun parseRoundTrips() {
        val produce = UnitKey.parse("kitchen/fridge|produce")
        assertEquals(UnitKey("kitchen/fridge", Role.Produce, null), produce)
        val recognize = UnitKey.parse("work/boss|recognize|керівник")
        assertEquals(UnitKey("work/boss", Role.Recognize, "керівник"), recognize)
        assertEquals("work/boss|recognize|керівник", recognize!!.encoded)
    }

    @Test
    fun parseRejectsMalformedKeys() {
        for (bad in listOf(
            "", "x", "x|", "x|bogus", "x|recognize", "x|recognize|",
            "|produce", "x|produce|extra", "x|Produce",
        )) {
            assertNull(UnitKey.parse(bad), "should reject \"$bad\"")
        }
    }

    @Test
    fun constructionEnforcesRoleFormPairing() {
        assertFailsWith<IllegalArgumentException> { UnitKey("a", Role.Produce, "form") }
        assertFailsWith<IllegalArgumentException> { UnitKey("a", Role.Recognize, null) }
        assertFailsWith<IllegalArgumentException> { UnitKey("a|b", Role.Produce, null) }
        assertFailsWith<IllegalArgumentException> { UnitKey("a", Role.Recognize, "") }
    }

    @Test
    fun schedulingDerivesItsKey() {
        val addedAt = Instant.fromEpochMilliseconds(0)
        assertEquals(
            "alpha/mouse|produce",
            UnitScheduling(cardId = "alpha/mouse", role = Role.Produce, addedAt = addedAt).key,
        )
        assertEquals(
            "alpha/mouse|recognize|мишеня",
            UnitScheduling(
                cardId = "alpha/mouse", role = Role.Recognize, form = "мишеня", addedAt = addedAt,
            ).key,
        )
    }
}
