package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `store-legacy-v1-box.json` is the one real v1 document the converter is measured against:
 * it holds [StoreFixture.state], written by the build the previous schema shipped in.
 * It goes when the converter does.
 */
class StoreGoldenTest {

    private val legacyV1: String by lazy {
        checkNotNull(javaClass.classLoader.getResourceAsStream("store-legacy-v1-box.json"))
            .readBytes().decodeToString()
    }

    /**
     * The box a device still holds arrives as exactly the box that replaces it, short of the
     * known language: a v1 document names none, and the converter has none to invent. The app
     * stamps it on the next save, which is what stamping it here stands in for.
     */
    @Test
    fun aRealV1DocumentConvertsToTheSameBox() {
        val loaded = StoreCodec.load(legacyV1)
        assertEquals(true, loaded.converted)
        assertEquals(null, loaded.box.source)
        assertEquals(
            StoreCodec.encode(StoredBox.of(StoreFixture.state())),
            StoreCodec.encode(loaded.box.copy(source = StoreFixture.stamp.source)),
        )
    }
}
