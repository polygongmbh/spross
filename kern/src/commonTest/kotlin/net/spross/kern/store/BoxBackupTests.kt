package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoxBackupTests {

    private val state = StoreFixture.state()
    private val document = StoreCodec.encode(state)

    @Test
    fun restoresEveryBoxItCarries() {
        val restored = BoxBackup.decode(BoxBackup.encode(mapOf("uk" to document)))
        assertEquals(setOf("uk"), restored.keys)
        assertEquals(state, StoreCodec.decode(restored.getValue("uk")).join(StoreFixture.cards, StoreFixture.stamp))
    }

    @Test
    fun refusesWhatIsNotABackup() {
        assertFailsWith<StoreFormatException> { BoxBackup.decode(document) }
        assertFailsWith<StoreFormatException> { BoxBackup.decode("""{"format":"other","version":1,"boxes":{}}""") }
        assertFailsWith<StoreFormatException> { BoxBackup.decode("not json") }
    }

    @Test
    fun oneUnreadableBoxRefusesTheWholeFile() {
        val backup = """{"boxes":{"de":{"broken":true},"uk":$document},"format":"spross-box-backup","version":1}"""
        assertFailsWith<StoreFormatException> { BoxBackup.decode(backup) }
    }

    @Test
    fun aBoxFiledUnderAnotherTargetIsRefused() {
        assertFailsWith<StoreFormatException> { BoxBackup.decode(BoxBackup.encode(mapOf("de" to document))) }
    }
}
