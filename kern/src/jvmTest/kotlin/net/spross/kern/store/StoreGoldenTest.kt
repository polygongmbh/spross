package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the exact bytes of one full encoded box document (approved fixture).
 * Any intentional schema change must re-approve resources/store-golden-box.json
 * and bump [StoreCodec.SCHEMA_VERSION] when the shape changes incompatibly.
 */
class StoreGoldenTest {

    private val approved: String by lazy {
        checkNotNull(javaClass.classLoader.getResourceAsStream("store-golden-box.json"))
            .readBytes().decodeToString()
    }

    @Test
    fun encodedDocumentMatchesApprovedFixture() {
        assertEquals(approved, StoreCodec.encode(StoreFixture.state()))
    }

    @Test
    fun approvedDocumentIsByteStableAcrossDecodeEncode() {
        val decoded = StoreCodec.decode(approved)
        assertEquals(approved, StoreCodec.encode(decoded.join(StoreFixture.cards, StoreFixture.stamp)))
    }
}
