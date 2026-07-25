package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the exact bytes of one full encoded box document (approved fixture).
 * Any intentional schema change must re-approve resources/store-golden-box.json
 * and bump [StoreCodec.SCHEMA_VERSION] when the shape changes incompatibly.
 *
 * This guards the SERIALIZATION FORMAT — key order, field names, number and date
 * formatting — because the store is the one place real user data lives and silent
 * drift there misreads previously written boxes. The card ids inside are opaque
 * payload the codec never interprets, so they are deliberately synthetic
 * (`fixture-noun`, …): a fixture that mimicked catalog ids would look stale every
 * time the catalog changed and invite re-approval for cosmetic reasons, which is
 * exactly what a byte-pinned golden must not train.
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
