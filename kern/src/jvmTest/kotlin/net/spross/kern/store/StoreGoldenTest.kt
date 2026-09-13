package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the exact bytes of one full encoded box (approved fixture).
 * Any intentional schema change must re-approve resources/store-golden.json
 * and bump [StoreCodec.SCHEMA_VERSION] when the shape changes incompatibly.
 *
 * This guards the SERIALIZATION FORMAT — key order, field names, number formatting —
 * because the store is the one place real user data lives and silent drift there misreads
 * previously written boxes. The card ids inside are opaque payload the codec never
 * interprets, so they are deliberately synthetic (`fixture-noun`, …): a fixture that
 * mimicked catalog ids would look stale every time the catalog changed and invite
 * re-approval for cosmetic reasons, which is exactly what a byte-pinned golden must not train.
 *
 * `store-legacy-v1-box.json` is the golden the PREVIOUS schema pinned, kept as the one real
 * v1 document the converter is measured against. It goes when the converter does.
 */
class StoreGoldenTest {

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)).readBytes().decodeToString()

    private val approved: String by lazy { resource("store-golden.json") }
    private val legacyV1: String by lazy { resource("store-legacy-v1-box.json") }

    @Test
    fun encodedBoxMatchesApprovedFixture() {
        assertEquals(approved, StoreCodec.encode(StoredBox.of(StoreFixture.state())))
    }

    @Test
    fun approvedBoxIsByteStableAcrossDecodeEncode() {
        assertEquals(approved, StoreCodec.encode(StoreCodec.decode(approved)))
    }

    /** The box a device still holds arrives as exactly the box that replaces it. */
    @Test
    fun aRealV1DocumentConvertsToTheSameBox() {
        val loaded = StoreCodec.load(legacyV1)
        assertEquals(true, loaded.converted)
        assertEquals(approved, StoreCodec.encode(loaded.box))
    }
}
